package fl.wearable.autosport.networking.clients

import android.util.Log
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.processors.PublishProcessor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.json
import fl.wearable.autosport.execution.SyftJob
import fl.wearable.autosport.networking.datamodels.NetworkModels
import fl.wearable.autosport.networking.datamodels.SocketResponse
import fl.wearable.autosport.networking.datamodels.syft.AuthenticationRequest
import fl.wearable.autosport.networking.datamodels.syft.AuthenticationResponse
import fl.wearable.autosport.networking.datamodels.syft.CycleRequest
import fl.wearable.autosport.networking.datamodels.syft.CycleResponseData
import fl.wearable.autosport.networking.datamodels.syft.ReportRequest
import fl.wearable.autosport.networking.datamodels.syft.ReportResponse
import fl.wearable.autosport.networking.datamodels.webRTC.InternalMessageRequest
import fl.wearable.autosport.networking.datamodels.webRTC.InternalMessageResponse
import fl.wearable.autosport.networking.datamodels.webRTC.JoinRoomRequest
import fl.wearable.autosport.networking.datamodels.webRTC.JoinRoomResponse
import fl.wearable.autosport.networking.requests.MessageTypes
import fl.wearable.autosport.networking.requests.NetworkingProtocol
import fl.wearable.autosport.networking.requests.REQUESTS
import fl.wearable.autosport.networking.requests.ResponseMessageTypes
import fl.wearable.autosport.networking.requests.SocketAPI
import fl.wearable.autosport.networking.requests.WebRTCMessageTypes
import fl.wearable.autosport.threading.ProcessSchedulers
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SocketClient"

/**
 * Used to communicate and exchange data throw web socket with PyGrid
 * @property syftWebSocket Create web socket connection
 * @property timeout Timeout period
 * @property schedulers Manage multi-threading operations
 * */
@ExperimentalUnsignedTypes
internal class SocketClient(
    private val syftWebSocket: SyftWebSocket,
    private val timeout: UInt = 20000u,
    private val schedulers: ProcessSchedulers
) : SocketAPI, Disposable {

    constructor(baseUrl: String, timeout: UInt, schedulers: ProcessSchedulers) :
            this(SyftWebSocket(NetworkingProtocol.WSS, baseUrl, timeout), timeout, schedulers)

    //Choosing stable kotlin serialization over default
    private val Json = Json(JsonConfiguration.Stable)

    // Check to manage resource usage
    @Volatile
    private var socketClientSubscribed = AtomicBoolean(false)

    // Emit socket messages to subscribers
    private val messageProcessor = PublishProcessor.create<NetworkModels>()

    // Manage and clear Disposables
    private val compositeDisposable = CompositeDisposable()

    /**
     * Authenticate socket connection with PyGrid
     * @see {@link fl.wearable.autosport.networking.datamodels.syft.AuthenticationRequest AuthenticationRequest}
     * */
    override fun authenticate(authRequest: AuthenticationRequest): Single<AuthenticationResponse> {
        connectWebSocket()
        Log.d(
            TAG,
            "sending message: " + serializeNetworkModel(
                REQUESTS.AUTHENTICATION,
                authRequest
            ).toString()
        )
        syftWebSocket.send(serializeNetworkModel(REQUESTS.AUTHENTICATION, authRequest))
        return messageProcessor.onBackpressureLatest()
                .ofType(AuthenticationResponse::class.java)
                .firstOrError()
    }

    /**
     * Request or get current active federated learning cycle
     * @see {@link fl.wearable.autosport.networking.datamodels.syft.CycleRequest CycleRequest}
     * */
    override fun getCycle(cycleRequest: CycleRequest): Single<CycleResponseData> {
        connectWebSocket()
        Log.d(
            TAG,
            "sending message: " + serializeNetworkModel(REQUESTS.CYCLE_REQUEST, cycleRequest)
        )
        syftWebSocket.send(serializeNetworkModel(REQUESTS.CYCLE_REQUEST, cycleRequest))
        return messageProcessor.onBackpressureBuffer()
                .ofType(CycleResponseData::class.java)
                .firstOrError()
    }

    //todo handle backpressure and first or error
    /**
     * Report model state to PyGrid after the cycle completes
     * */
    override fun report(reportRequest: ReportRequest): Single<ReportResponse> {
        connectWebSocket()
        syftWebSocket.send(serializeNetworkModel(REQUESTS.REPORT, reportRequest))
        return messageProcessor.onBackpressureDrop()
                .ofType(ReportResponse::class.java)
                .firstOrError()
    }

    //todo handle backpressure and first or error
    /**
     * Used by WebRTC to request PyGrid joining a FL cycle
     * */
    override fun joinRoom(joinRoomRequest: JoinRoomRequest): Single<JoinRoomResponse> {
        connectWebSocket()
        syftWebSocket.send(
            serializeNetworkModel(
                WebRTCMessageTypes.WEBRTC_JOIN_ROOM,
                joinRoomRequest
            )
        )
        return messageProcessor.onBackpressureBuffer()
                .ofType(JoinRoomResponse::class.java)
                .firstOrError()
    }

    //todo handle backpressure and first or error
    /**
     * Used by WebRTC connection to send message via PyGrid
     * */
    override fun sendInternalMessage(internalMessageRequest: InternalMessageRequest): Single<InternalMessageResponse> {
        connectWebSocket()
        syftWebSocket.send(serializeNetworkModel(REQUESTS.WEBRTC_INTERNAL, internalMessageRequest))
        return messageProcessor.onBackpressureBuffer()
                .ofType(InternalMessageResponse::class.java)
                .first(null)
    }

    /**
     * Listen and handle socket events
     * */
    fun initiateNewWebSocket() {
        compositeDisposable.add(syftWebSocket.start()
                .map {
                    when (it) {
                        is NetworkMessage.SocketOpen -> {
                        }
                        is NetworkMessage.SocketError -> Log.e(
                            TAG,
                            "socket error",
                            it.throwable
                        )
                        is NetworkMessage.MessageReceived -> {
                            Log.d(TAG, "received the message " + it.message)
                            emitMessage(deserializeSocket(it.message))
                        }
                    }
                }
                .subscribeOn(schedulers.computeThreadScheduler)
                .observeOn(schedulers.calleeThreadScheduler)
                .doOnEach {
                    if (!socketClientSubscribed.get())
                        socketClientSubscribed.set(true)
                }
                .subscribe()
        )
    }

    // Check socket status to free resources
    override fun isDisposed() = socketClientSubscribed.get()

    // Free resources
    override fun dispose() {
        compositeDisposable.clear()
        if (isDisposed) {
            syftWebSocket.close()
            socketClientSubscribed.set(false)
            Log.d(TAG, "Socket Client Disposed")
        } else
            Log.d(TAG,"socket client already disposed")
    }

    /**
     * Prevent initializing new socket connection if any exists
     * */
    private fun connectWebSocket() {
        if (socketClientSubscribed.get())
            return
        initiateNewWebSocket()
    }

    /**
     * Emit message to the subscribers
     * */
    private fun emitMessage(response: SocketResponse) {
        messageProcessor.offer(response.data)
    }

    /**
     * Parse incoming message
     * */
    private fun deserializeSocket(socketMessage: String): SocketResponse {
        return Json.parse(SocketResponse.serializer(), socketMessage)
    }

    /**
     * Serialize message to be sent to PyGrid
     * */
    private fun serializeNetworkModel(types: MessageTypes, data: NetworkModels) = json {
        TYPE to types.value
            if (types is ResponseMessageTypes)
                DATA to types.serialize(data)
            else
            //todo change this appropriately when needed
                DATA to data.toString()
        }
}
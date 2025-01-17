package fl.wearable.autosport.domain

import android.content.Context
import android.net.NetworkCapabilities
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import fl.wearable.autosport.networking.clients.HttpClient
import fl.wearable.autosport.networking.clients.SocketClient
import fl.wearable.autosport.networking.requests.CommunicationAPI
import fl.wearable.autosport.threading.ProcessSchedulers
import java.io.File

@ExperimentalUnsignedTypes
class SyftConfiguration internal constructor(
    val context: Context,
    val networkingSchedulers: ProcessSchedulers,
    val computeSchedulers: ProcessSchedulers,
    val filesDir: File,
    val monitorDevice : Boolean,
    val batteryCheckEnabled: Boolean,
    val networkConstraints: List<Int>,
    val transportMedium: Int,
    val cacheTimeOut: Long,
    val maxConcurrentJobs: Int,
    private val socketClient: SocketClient,
    private val httpClient: HttpClient,
    private val messagingClient: NetworkingClients
) {
    companion object {
        fun builder(context: Context, baseUrl: String) = SyftConfigBuilder(context, baseUrl)
    }

    internal fun getDownloader() = httpClient.apiClient

    internal fun getSignallingClient(): CommunicationAPI = when (messagingClient) {
        NetworkingClients.HTTP -> httpClient.apiClient
        NetworkingClients.SOCKET -> socketClient
    }

    internal fun getWebRTCSignallingClient(): SocketClient = socketClient

    class SyftConfigBuilder(private val context: Context, baseUrl: String) {

        private var networkingSchedulers: ProcessSchedulers =object : ProcessSchedulers {
            override val computeThreadScheduler: Scheduler
                get() = Schedulers.io()
            override val calleeThreadScheduler: Scheduler
                get() = AndroidSchedulers.mainThread()
        }

        private var computeSchedulers: ProcessSchedulers = object : ProcessSchedulers {
            override val computeThreadScheduler: Scheduler
                get() = Schedulers.computation()
            override val calleeThreadScheduler: Scheduler
                get() = Schedulers.single()
        }
        private var socketClient = SocketClient(baseUrl, 20000u, networkingSchedulers)
        private var httpClient = HttpClient.initialize(baseUrl)
        private var filesDir = context.filesDir
        private var batteryCheckEnabled = true
        private var maxConcurrentJobs: Int = 1
        private var messagingClient: NetworkingClients = NetworkingClients.SOCKET
        private var cacheTimeOut: Long = 100000
        private var monitorDevice: Boolean = true

        private val networkConstraints = mutableMapOf(
            NetworkCapabilities.NET_CAPABILITY_INTERNET to true,
            NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED to true,
            NetworkCapabilities.NET_CAPABILITY_NOT_METERED to true
        )
        private var networkTransportMedium = NetworkCapabilities.TRANSPORT_WIFI

        fun build(): SyftConfiguration {
            val constraintList = networkConstraints.filterValues { it }.keys.toList()
            return SyftConfiguration(
                context,
                networkingSchedulers,
                computeSchedulers,
                filesDir,
                monitorDevice,
                batteryCheckEnabled,
                constraintList,
                networkTransportMedium,
                cacheTimeOut,
                maxConcurrentJobs,
                socketClient,
                httpClient,
                messagingClient
            )
        }

        fun disableBatteryCheck(): SyftConfigBuilder {
            batteryCheckEnabled = false
            return this
        }

        fun enableBatteryCheck(): SyftConfigBuilder {
            batteryCheckEnabled = true
            return this
        }

        fun setMessagingClient(messagingClient : NetworkingClients) : SyftConfigBuilder {
            this.messagingClient = messagingClient
            return this
        }

        fun enableCellularData(): SyftConfigBuilder {
            networkTransportMedium = NetworkCapabilities.TRANSPORT_CELLULAR
            return this
        }

        fun enableMeteredData(): SyftConfigBuilder {
            if (networkConstraints.containsKey(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
                networkConstraints.remove(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            return this
        }

        fun setCacheTimeout(timeout: Long): SyftConfigBuilder {
            this.cacheTimeOut = timeout
            return this
        }

        fun setNetworkingScheduler(scheduler: ProcessSchedulers): SyftConfigBuilder {
            this.networkingSchedulers = scheduler
            return this
        }

        fun setComputeScheduler(computeSchedulers: ProcessSchedulers): SyftConfigBuilder {
            this.computeSchedulers = computeSchedulers
            return this
        }
//todo add this when pygrid supports multiple jobs
//        fun setMaxConcurrentJobs(numJobs: Int): SyftConfigBuilder {
//            this.maxConcurrentJobs = numJobs
//            return this
//        }

        fun disableBackgroundServiceExecution(): SyftConfigBuilder {
            this.monitorDevice = true
            return this
        }

        fun enableBackgroundServiceExecution(): SyftConfigBuilder {
            this.monitorDevice = false
            return this
        }

        fun setFilesDir(filesDir: File): SyftConfigBuilder {
            this.filesDir = filesDir
            return this
        }
    }

    enum class NetworkingClients {
        HTTP, SOCKET
    }
}
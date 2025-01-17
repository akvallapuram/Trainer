package fl.wearable.autosport.networking.datamodels.webRTC

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import fl.wearable.autosport.networking.datamodels.NetworkModels

internal const val WEBRTC_INTERNAL_TYPE = "webrtc_internal"

@Serializable
internal data class InternalMessageResponse(
    val type: String,
    @SerialName("worker_id")
    val newWorkerId: String,
    @SerialName("sdp_string")
    val sessionDescription: String
) : NetworkModels()

@Serializable
internal data class InternalMessageRequest(
    @SerialName("worker_id")
    val workerId: String,
    @SerialName("scope_id")
    val scopeId: String,
    val target: String,
    val type: String,
    val message: String
) : NetworkModels()
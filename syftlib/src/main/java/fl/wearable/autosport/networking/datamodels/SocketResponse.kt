package fl.wearable.autosport.networking.datamodels

import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.internal.SerialClassDescImpl
import kotlinx.serialization.json.JsonInput
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonOutput
import kotlinx.serialization.json.json
import fl.wearable.autosport.networking.requests.REQUESTS
import fl.wearable.autosport.networking.requests.ResponseMessageTypes

private const val TAG = "SocketSerializer"

@Serializable(with = SocketSerializer::class)
internal data class SocketResponse(
    @SerialName("type")
    val typesResponse: ResponseMessageTypes,
    val data: NetworkModels
)

@Suppress("UNCHECKED_CAST")
@Serializer(forClass = SocketResponse::class)
internal class SocketSerializer : KSerializer<SocketResponse> {
    override val descriptor: SerialDescriptor
        get() = SerialClassDescImpl("SocketSerializer")

    override fun deserialize(decoder: Decoder): SocketResponse {
        val input = decoder as? JsonInput
                    ?: throw SerializationException("This class can be loaded only by Json")
        val response = input.decodeJson() as? JsonObject
                       ?: throw SerializationException("Expected JsonObject")
        val type = REQUESTS.getObjectFromString(response.getPrimitive("type").content)
        val data = type.parseJson(response["data"].toString())
        return SocketResponse(type, data)
    }

    override fun serialize(encoder: Encoder, obj: SocketResponse) {
        val output = encoder as? JsonOutput
                     ?: throw SerializationException("This class can be saved only by Json")

        output.encodeJson(json {
            "type" to obj.typesResponse.value
            "data" to obj.typesResponse.serialize(obj.data)
        })
    }

}
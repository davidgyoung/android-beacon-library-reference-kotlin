import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import org.altbeacon.beacon.Beacon
import java.time.Instant.ofEpochMilli



interface  LogRepository{
        suspend fun consumeLog() : Entries
        suspend fun appendLog(entries: List<Entry>)
    }
    class BLELogRepository ()  : LogRepository{
        // Mutex to make writes to cached values thread-safe.
        private val logMutex = Mutex()

        // Cache of the latest tag rssi and fields
        private var recentTagLog: MutableList<Entry> = mutableListOf()

        //provide service with copy of current log, clear log to start again
        override suspend fun consumeLog(): Entries {
            val logCopy = logMutex.withLock {
                val copy = recentTagLog.map { it.deepCopy() }
                recentTagLog.clear()
                copy
            }
            return Entries(logCopy)
        }

        //add to log
         fun appendLog(beacons: Collection<Beacon>) {
            runBlocking {
                appendLog(beaconToEntry(beacons))
            }
        }
        override suspend fun appendLog(entries: List<Entry>) {
            logMutex.withLock {
                this.recentTagLog.addAll(entries)
            }
        }
        private fun beaconToEntry(beacons : Collection<Beacon>) : List<Entry>
        {
            val entries : List<Entry> = beacons.map{
                Entry(
                    //use first detection and average location for every beacon
                    time = ofEpochMilli(it.firstCycleDetectionTimestamp).toKotlinInstant().toLocalDateTime(timeZone = TimeZone.currentSystemDefault()),
                    tag =Tag(it.id3.toInt().toUShort(),it.id2.toInt().toUShort(),it.id1.toUuid()),
                    //dist based on rssi running average at,
                    distance = it.distance,
                    //GPS not required atm
                    position = Position(0.0,0.0)
                    )
            }
            return entries
        }
    }




@Serializable
data class Entry(
    // Give 0 value for the tag field not used.
    var time: LocalDateTime,
    var tag: Tag =  Tag(0U,0U,UUID(0,0)),
    @SerialName("tag_id")
    var tagID : Int = 0,
    var distance: Double,
    @SerialName("device_position")
    var position : Position
) {
    //deep copy by serialization, if fields change, it is robust
    fun deepCopy() : Entry {
        return Json.decodeFromString(Json.encodeToString(serializer(),this))
    }
}


@Serializable
data class Entries (
    var entries : List<Entry>){
}


@Serializable
data class RegisterStatus(
    var status : Int
)
@Serializable
data class LogStatus(
    var status : List<Int>
)

@Serializable
data class Position (
    var longitude : Double,
    var latitude : Double
)

@Serializable
data class Tag (
    var major : UShort,
    var minor : UShort,
    @Serializable(with= UUIDSerializer::class)
    var uuid  : UUID
)
//Custom Serializer Needed because UUIDs are not
//deserialized in default. This is only used for this field in Tag
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }
}

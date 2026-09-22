package app.tide.body

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant

/**
 * What Body reads, and the only place Health Connect is spoken to.
 *
 * Behind an interface because the alternative is a screen that can only be
 * tested on a phone with Health Connect installed, data in it and permissions
 * granted. The fake in the tests is what makes the states provable: not
 * installed, installed but not granted, granted and empty, granted with data.
 *
 * **Read-only, always.** Tide writes nothing to Health Connect. Whatever your
 * watch already records is shown here; nothing here changes it.
 */
interface HealthSource {

    /** Whether Health Connect exists on this device at all. */
    suspend fun availability(): Availability

    /** True when every permission in [PERMISSIONS] has been granted. */
    suspend fun hasPermissions(): Boolean

    /** A window's worth of readings. Anything missing comes back null. */
    suspend fun read(from: Instant, to: Instant): HealthReadings

    enum class Availability { Available, NotInstalled, NotSupported }

    companion object {
        /**
         * Four readings, and no more.
         *
         * Each one earns its place by being something Body will show. Asking
         * for a permission the app has no screen for is how an app ends up with
         * a scary permission sheet and nothing to justify it.
         */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
        )
    }
}

/**
 * Everything Body knows for a window. Null means "no reading", which the screen
 * prints as an absence rather than as a zero.
 */
data class HealthReadings(
    val steps: Long? = null,
    val sleep: Duration? = null,
    val restingHeartRateBpm: Int? = null,
    val weightKg: Double? = null,
    val weightAt: Instant? = null,
)

/** The real one. Everything it can throw is caught at the edge in [BodyViewModel]. */
class AndroidHealthSource(private val context: Context) : HealthSource {

    private val client: HealthConnectClient? by lazy {
        runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    override suspend fun availability(): HealthSource.Availability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthSource.Availability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthSource.Availability.NotInstalled
            else -> HealthSource.Availability.NotSupported
        }

    override suspend fun hasPermissions(): Boolean {
        val granted = client?.permissionController?.getGrantedPermissions() ?: return false
        return granted.containsAll(HealthSource.PERMISSIONS)
    }

    override suspend fun read(from: Instant, to: Instant): HealthReadings {
        val client = client ?: return HealthReadings()
        val range = TimeRangeFilter.between(from, to)

        val steps = client.readRecords(ReadRecordsRequest(StepsRecord::class, range))
            .records.sumOf { it.count }

        val sleep = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range))
            .records.fold(Duration.ZERO) { total, record ->
                total.plus(Duration.between(record.startTime, record.endTime))
            }

        // The lowest sample in the window, which is the closest thing to a
        // resting rate that can be had without the watch's own calculation.
        val heart = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range))
            .records.flatMap { it.samples }.minOfOrNull { it.beatsPerMinute }

        val weight = client.readRecords(ReadRecordsRequest(WeightRecord::class, range))
            .records.maxByOrNull { it.time }

        return HealthReadings(
            steps = steps.takeIf { it > 0 },
            sleep = sleep.takeIf { !it.isZero },
            restingHeartRateBpm = heart?.toInt(),
            weightKg = weight?.weight?.inKilograms,
            weightAt = weight?.time,
        )
    }
}

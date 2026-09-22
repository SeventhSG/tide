package app.tide.core.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The migration, run against a real version 1 database with real data in it.
 *
 * This test is the promise the project makes about your data. There is no
 * destructive fallback and no server to restore from, so the only thing
 * standing between a schema change and a wiped year of training is a migration
 * that has actually been run rather than one that has been written.
 *
 * It inserts a session and a set before migrating and checks they are still
 * there afterwards. A migration that creates the new tables correctly but
 * drops a table on the way would pass a "does it open" test and fail this one.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TideDatabase::class.java,
    )

    @Test
    fun `version 1 data survives the move to version 2`() {
        helper.createDatabase(name, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO exercise (id, name, primaryMuscle, secondaryMuscles, equipment,
                                      isBodyweight, isCustom, progressionRule, notes)
                VALUES ('squat', 'Back squat', 'Quads', 'Glutes', 'Barbell', 0, 0, 'Linear:2.5:3:0.9', NULL)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO session (id, routineId, dayIndex, startedAt, endedAt,
                                     bodyWeightKg, note, isRetroactive)
                VALUES ('s1', NULL, NULL, 1000, 2000, NULL, NULL, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO workout_set (id, sessionId, exerciseId, orderInSession, kind,
                                         loadKg, reps, repsPerSide, durationSec, distanceM,
                                         rir, supersetGroup, dropOfSetId, completedAt)
                VALUES ('w1', 's1', 'squat', 0, 'Standard', 100.0, 5, NULL, NULL, NULL,
                        NULL, NULL, NULL, 1500)
                """.trimIndent(),
            )
        }

        // Room validates the resulting schema against 2.json here, so a
        // hand-written CREATE TABLE that drifts from the entity fails loudly.
        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT id, loadKg, reps FROM workout_set").use { cursor ->
                assertTrue("the set must still be there", cursor.moveToFirst())
                assertEquals("w1", cursor.getString(0))
                assertEquals(100.0, cursor.getDouble(1), 0.001)
                assertEquals(5, cursor.getInt(2))
            }
            db.query("SELECT COUNT(*) FROM session").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            // And the new tables are usable, not merely present.
            db.execSQL(
                """
                INSERT INTO schedule_rule (id, title, kind, recurrence, anchorEpochDay,
                                           untilEpochDay, createdAt, archivedAt)
                VALUES ('r1', 'Train', 'Training', 'TimesPerWeek:3', 20000, NULL, 1000, NULL)
                """.trimIndent(),
            )
            db.query("SELECT COUNT(*) FROM schedule_rule").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `the whole chain runs, from version 1 to the current version`() {
        // The path a phone that installed the app early actually takes. Each
        // migration is tested on its own above; this is the one that catches a
        // pair that each work but do not compose.
        helper.createDatabase(name, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO session (id, routineId, dayIndex, startedAt, endedAt,
                                     bodyWeightKg, note, isRetroactive)
                VALUES ('s1', NULL, NULL, 1000, 2000, NULL, NULL, 0)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(name, 3, true, MIGRATION_1_2, MIGRATION_2_3).use { db ->
            db.query("SELECT COUNT(*) FROM session").use { cursor ->
                cursor.moveToFirst()
                assertEquals("a session from version 1 must survive both steps", 1, cursor.getInt(0))
            }
            db.execSQL(
                """
                INSERT INTO notification_ledger (notificationKey, title, tier, postedAt, inDigest)
                VALUES ('k', 'Legs', 'Quiet', 5000, 0)
                """.trimIndent(),
            )
            db.query("SELECT COUNT(*) FROM notification_ledger").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `a fresh version 3 database opens and carries every table`() {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TideDatabase::class.java,
        ).allowMainThreadQueries().build()

        db.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table'",
        ).use { cursor ->
            val tables = buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            listOf(
                "exercise", "session", "workout_set", "exercise_state",
                "schedule_rule", "skipped_occurrence", "notification_ledger",
            ).forEach {
                assertTrue("$it missing from a fresh build", it in tables)
            }
        }
        db.close()
    }
}

package app.tide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.tide.body.HealthSource
import app.tide.core.data.db.Equipment
import app.tide.core.data.db.Muscle
import app.tide.core.design.TideTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The sections added in v0.1.1, rendered at phone size.
 *
 * Roborazzi does not run the AGSL shader and does not animate, so the wave
 * transition and the boot wave cannot be photographed here: what these prove is
 * layout, type, contrast and the states, which is what a still can prove.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class V011ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            TideTheme {
                Box(Modifier.size(411.dp, 891.dp)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun todayWithScaffold() {
        // Today used to carry its own LiquidGlassNav, a leftover from before
        // TideScaffold existed, so this screen actually showed two bars
        // stacked. This wraps it exactly as MainActivity does, which is the
        // only way that regression would ever show up in a screenshot.
        capture("today-with-nav") {
            TideScaffold(section = Section.Today, onSelectSection = {}) {
                TodayScreen(
                    TodayUiState(
                        dateLabel = "TUE 22 SEPTEMBER",
                        headline = "No session\nyet today.",
                        subline = "Last session was 2 days ago.",
                        volumeLast7Days = "12 480 kg",
                        calendarMonth = (1..22).map {
                            TodayUiState.CalendarDay(
                                date = LocalDate.of(2026, 9, it),
                                label = it.toString(),
                                trained = it == 20,
                                planned = false,
                                moneyDue = false,
                                isToday = it == 22,
                            )
                        },
                        weekSummary = "1 SESSION",
                    ),
                )
            }
        }
    }

    @Test
    fun trainHub() {
        capture("train") {
            TideScaffold(section = Section.Train, onSelectSection = {}) {
                TrainScreen(
                    TrainUiState(
                        windowLabel = "LAST 28 DAYS",
                        sessions = 11,
                        workingSets = 142,
                        volume = "48 260 kg",
                        lastTrainedLabel = "YESTERDAY",
                        hasHistory = true,
                    ),
                )
            }
        }
    }

    @Test
    fun trainHubWithNothingLogged() {
        capture("train-empty") {
            TideScaffold(section = Section.Train, onSelectSection = {}) {
                TrainScreen(TrainUiState(windowLabel = "LAST 28 DAYS"))
            }
        }
    }

    @Test
    fun planner() {
        capture("planner") {
            PlannerScreen(
                PlannerUiState(
                    slots = listOf(
                        PlannerUiState.Slot(0, "M", "Monday", 3, isCurrent = false, isSelected = true),
                        PlannerUiState.Slot(1, "T", "Tuesday", 0, isCurrent = false, isSelected = false),
                        PlannerUiState.Slot(2, "W", "Wednesday", 3, isCurrent = true, isSelected = false),
                        PlannerUiState.Slot(3, "T", "Thursday", 0, isCurrent = false, isSelected = false),
                        PlannerUiState.Slot(4, "F", "Friday", 4, isCurrent = false, isSelected = false),
                        PlannerUiState.Slot(5, "S", "Saturday", 0, isCurrent = false, isSelected = false),
                        PlannerUiState.Slot(6, "S", "Sunday", 0, isCurrent = false, isSelected = false),
                    ),
                    selectedDay = 0,
                    exercises = listOf(
                        PlannerUiState.Line(
                            "1", 1, "Back squat", "3 x 5",
                            Equipment.Barbell, Muscle.Quads,
                            canMoveUp = false, canMoveDown = true,
                        ),
                        PlannerUiState.Line(
                            "2", 2, "Bench press", "3 x 5",
                            Equipment.Barbell, Muscle.Chest,
                            canMoveUp = true, canMoveDown = true,
                        ),
                        PlannerUiState.Line(
                            "3", 3, "Pull-up", "3 x 8",
                            Equipment.Bodyweight, Muscle.Lats,
                            canMoveUp = true, canMoveDown = false,
                        ),
                    ),
                    plannedTotal = 10,
                    trainingDays = 3,
                ),
            )
        }
    }

    @Test
    fun plannerRestDay() {
        capture("planner-rest") {
            PlannerScreen(
                PlannerUiState(
                    slots = (0..6).map {
                        PlannerUiState.Slot(
                            it,
                            listOf("M", "T", "W", "T", "F", "S", "S")[it],
                            "Day",
                            count = 0,
                            isCurrent = it == 1,
                            isSelected = it == 1,
                        )
                    },
                    selectedDay = 1,
                ),
            )
        }
    }

    @Test
    fun plannerRotation() {
        capture("planner-rotation") {
            PlannerScreen(
                PlannerUiState(
                    mode = app.tide.core.data.db.PlanMode.Rotation,
                    slots = listOf(
                        PlannerUiState.Slot(0, "Push", "Push", 3, isCurrent = false, isSelected = false),
                        PlannerUiState.Slot(1, "Pull", "Pull", 3, isCurrent = true, isSelected = true),
                        PlannerUiState.Slot(2, "Legs", "Legs", 0, isCurrent = false, isSelected = false),
                    ),
                    selectedDay = 1,
                    exercises = listOf(
                        PlannerUiState.Line(
                            "1", 1, "Barbell row", "3 x 8",
                            Equipment.Barbell, Muscle.Back,
                            canMoveUp = false, canMoveDown = true,
                        ),
                        PlannerUiState.Line(
                            "2", 2, "Lat pulldown", "3 x 10",
                            Equipment.Cable, Muscle.Lats,
                            canMoveUp = true, canMoveDown = false,
                        ),
                    ),
                    plannedTotal = 6,
                    trainingDays = 2,
                ),
            )
        }
    }

    @Test
    fun bodyConnected() {
        capture("body") {
            TideScaffold(section = Section.Body, onSelectSection = {}) {
                BodyScreen(
                    BodyUiState(
                        granted = true,
                        steps = "8 420",
                        sleep = "6h 41m",
                        heartRate = "54 bpm",
                        weight = "81.4 kg",
                        weightAge = "YESTERDAY",
                    ),
                )
            }
        }
    }

    @Test
    fun bodyNotConnected() {
        capture("body-not-connected") {
            TideScaffold(section = Section.Body, onSelectSection = {}) {
                BodyScreen(
                    BodyUiState(
                        availability = HealthSource.Availability.Available,
                        granted = false,
                    ),
                )
            }
        }
    }

    @Test
    fun askInstalling() {
        capture("ask") {
            TideScaffold(section = Section.Ask, onSelectSection = {}) {
                AskScreen(
                    AskUiState(
                        modelName = "Qwen2.5 0.5B Instruct, Q4_K_M",
                        modelLicence = "Apache-2.0",
                        approximateSizeLabel = "400 MB",
                        installing = true,
                        progress = 0.42f,
                        downloadedLabel = "168 MB of 400 MB",
                    ),
                )
            }
        }
    }

    @Test
    fun askBeforeInstalling() {
        capture("ask-idle") {
            TideScaffold(section = Section.Ask, onSelectSection = {}) {
                AskScreen(
                    AskUiState(
                        modelName = "Qwen2.5 0.5B Instruct, Q4_K_M",
                        modelLicence = "Apache-2.0",
                        approximateSizeLabel = "400 MB",
                    ),
                )
            }
        }
    }

    @Test
    fun money() {
        capture("money") {
            TideScaffold(section = Section.Money, onSelectSection = {}) {
                MoneyScreen(MoneyUiState())
            }
        }
    }

    @Test
    fun moneyWithSubscriptions() {
        capture("money-subscriptions") {
            TideScaffold(section = Section.Money, onSelectSection = {}) {
                MoneyScreen(
                    MoneyUiState(
                        subscriptions = listOf(
                            MoneyUiState.Subscription(
                                id = "1", name = "Gym", amountLabel = "45",
                                nextRenewal = null, nextRenewalLabel = "1 OCTOBER",
                            ),
                            MoneyUiState.Subscription(
                                id = "2", name = "Streaming", amountLabel = "12.99",
                                nextRenewal = null, nextRenewalLabel = "8 OCTOBER",
                            ),
                        ),
                        monthlyTotalLabel = "57.99",
                    ),
                )
            }
        }
    }

    @Test
    fun settingsWithSound() {
        capture("settings") {
            SettingsScreen(
                SettingsUiState(
                    digestEnabled = true,
                    digestAt = "08:00",
                    quietHoursEnabled = true,
                    quietStart = "22:00",
                    quietEnd = "07:00",
                    permissionGranted = true,
                    soundPlaying = false,
                    soundVolume = "50%",
                ),
            )
        }
    }
}

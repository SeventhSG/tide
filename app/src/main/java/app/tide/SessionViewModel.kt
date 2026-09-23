package app.tide

import app.tide.core.data.db.Equipment
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.SetKind
import app.tide.core.data.db.WorkoutSetEntity
import app.tide.core.data.training.Prescription
import app.tide.core.data.training.ProgressionRule
import app.tide.core.data.training.RuleCodec
import app.tide.core.data.training.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Joins [SessionScreen] to [TrainingRepository].
 *
 * A plain class, not `androidx.lifecycle.ViewModel`. Session resumption already
 * lives in the repository, a second [TrainingRepository.startSession] returns
 * the session already open, so surviving a configuration change needs no
 * framework state holder here: on recreation this re-reads the same open row
 * rather than losing it. That keeps the object graph by hand, the way
 * [app.tide.core.data.Tide] is.
 *
 * The screen never sees a Room entity. Everything database shaped stops here.
 */
class SessionViewModel(
    private val exerciseId: String,
    private val repository: TrainingRepository,
    private val scope: CoroutineScope,
    private val defaultRule: ProgressionRule = ProgressionRule.Linear(),
    /**
     * A session left open past a couple of hours is worth a check, in case
     * Finish was never pressed. Called once the session is confirmed open,
     * including on every resume, so it is idempotent by (sessionId, startedAt)
     * rather than something to schedule only once.
     */
    private val onSessionOpen: (sessionId: String, startedAt: Long) -> Unit = { _, _ -> },
    /** Called once finishing actually completes, to cancel that check. */
    private val onSessionClosed: (sessionId: String) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(EMPTY_STATE)
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var sessionId: String? = null
    private var startedAt = now()
    private var exerciseName = exerciseId
    private var equipment: Equipment? = null
    private var muscle: Muscle? = null
    private var ruleLabel = ""
    private var targetSets: Int? = null
    private var lastTime: String? = null
    private var loadKg: Double? = null
    private var reps = 0
    private var latestSets: List<WorkoutSetEntity> = emptyList()
    private var sessionSets: List<WorkoutSetEntity> = emptyList()
    private var warmUp = false
    private var pendingRemoveId: String? = null
    private var confirmingFinish = false
    private var finishing = false
    private var summary: SessionSummary? = null

    init {
        scope.launch {
            val exercise = repository.exerciseById(exerciseId)
            exerciseName = exercise?.name ?: exerciseId
            equipment = exercise?.equipment
            muscle = exercise?.primaryMuscle
            val rule = RuleCodec.decode(exercise?.progressionRule) ?: defaultRule
            ruleLabel = formatRuleLabel(rule)

            val prescription = repository.prescriptionFor(exerciseId)
            val last = repository.lastPerformance(exerciseId)
            // No fallback. An exercise with no history has no set target, and a
            // made up one would read as a real plan on the screen.
            targetSets = prescription?.sets ?: last?.sets
            loadKg = prescription?.loadKg ?: last?.loadKg
            reps = prescription?.reps ?: last?.reps ?: 0
            lastTime = last?.let { formatSet(it.loadKg, it.reps, it.durationSec) }

            val id = repository.startSession()
            sessionId = id
            startedAt = repository.observeActiveSession().first()?.startedAt ?: now()
            onSessionOpen(id, startedAt)
            render()

            repository.observeSets(id).collect { sets ->
                sessionSets = sets
                latestSets = sets.filter { it.exerciseId == exerciseId }
                render()
            }
        }
    }

    fun onLoadChange(kg: Double) {
        loadKg = kg.coerceAtLeast(0.0)
        render()
    }

    fun onRepsChange(newReps: Int) {
        reps = newReps.coerceAtLeast(0)
        render()
    }

    /** Called on a UI tick to keep the elapsed clock live between logged sets. */
    fun tick() = render()

    /**
     * Marks what comes next as a warm-up.
     *
     * The single most important flag in the schema, and until now the app could
     * not set it: every set logged here counted toward progression, a 1RM and
     * the muscle map, which is exactly the corruption the warm-up filter exists
     * to prevent.
     */
    fun onToggleWarmUp() {
        warmUp = !warmUp
        render()
    }

    /** First tap asks, second tap removes. Nothing is deleted on one touch. */
    fun onRowTapped(setId: String) {
        pendingRemoveId = if (pendingRemoveId == setId) null else setId
        render()
    }

    fun onRemoveSet(setId: String) {
        pendingRemoveId = null
        scope.launch { repository.deleteSet(setId) }
        render()
    }

    fun onLogSet() {
        val id = sessionId ?: return
        val currentLoad = loadKg
        val currentReps = reps
        val kind = if (warmUp) SetKind.WarmUp else SetKind.Standard
        scope.launch {
            repository.logSet(id, exerciseId, kind, loadKg = currentLoad, reps = currentReps)
            // One warm-up at a time. Leaving the toggle on is how a working set
            // gets logged as a warm-up and quietly vanishes from progression.
            warmUp = false
            render()
        }
    }

    fun onFinishRequested() {
        if (sessionId == null || summary != null) return
        confirmingFinish = true
        render()
    }

    fun onFinishCancelled() {
        confirmingFinish = false
        render()
    }

    /**
     * Ends the session and runs progression, once.
     *
     * [finishing] stops a second tap from launching a second finish while the
     * first is still in the database. The repository refuses a second finish
     * as well; this just keeps the screen from asking.
     */
    fun onFinishConfirmed() {
        val id = sessionId ?: return
        if (finishing || summary != null) return
        finishing = true
        val endedAt = now()
        val workingSets = sessionSets.count { it.countsTowardProgression }
        val anySets = sessionSets.isNotEmpty()
        scope.launch {
            val decisions = repository.finishSession(id, defaultRule)
            onSessionClosed(id)
            summary = SessionSummary(
                duration = formatDuration(endedAt - startedAt),
                workingSets = workingSets,
                saved = anySets,
                exercises = decisions.map { d ->
                    SessionSummary.Decision(
                        exerciseName = repository.exerciseById(d.exerciseId)?.name ?: d.exerciseId,
                        reason = d.result.reason,
                        next = formatPrescription(d.result.next),
                    )
                },
            )
            confirmingFinish = false
            finishing = false
            render()
        }
    }

    private fun render() {
        val (logged, nextSetNumber) = buildLogged(latestSets)
        _state.value = SessionUiState(
            exerciseName = exerciseName,
            equipment = equipment,
            muscle = muscle,
            setNumber = nextSetNumber,
            targetSets = targetSets,
            ruleLabel = ruleLabel,
            loadKg = loadKg?.let(::trimNumber) ?: "",
            reps = reps.toString(),
            lastTime = lastTime,
            elapsed = formatElapsed(now() - startedAt),
            // Time since the last set went in, counted up. No target to count
            // down to: nothing in the app has said how long your rest should
            // be, and inventing 90 seconds would be inventing a prescription.
            restSinceLastSet = sessionSets.maxOfOrNull { it.completedAt }
                ?.let { formatRest(now() - it) },
            warmUp = warmUp,
            pendingRemoveId = pendingRemoveId,
            logged = logged,
            sessionSetCount = sessionSets.size,
            workingSetCount = sessionSets.count { it.countsTowardProgression },
            confirmingFinish = confirmingFinish,
            summary = summary,
        )
    }

    /** Every set kept, in order, with a warm-up labelled "W" instead of counted. */
    private fun buildLogged(
        sets: List<WorkoutSetEntity>,
    ): Pair<List<SessionUiState.LoggedSet>, Int> {
        var working = 0
        val logged = sets.map { s ->
            val isWarmUp = s.kind == SetKind.WarmUp
            val label = if (isWarmUp) "W" else (++working).toString()
            SessionUiState.LoggedSet(
                id = s.id,
                index = label,
                summary = formatSet(s.loadKg, s.reps, s.durationSec),
                rir = s.rir?.let { "RIR $it" },
                isWarmUp = isWarmUp,
            )
        }
        return logged to (working + 1)
    }

    companion object {
        private val EMPTY_STATE = SessionUiState(
            exerciseName = "",
            setNumber = 1,
            targetSets = null,
            ruleLabel = "",
            loadKg = "",
            reps = "",
            lastTime = null,
            elapsed = "00:00:00",
            restSinceLastSet = null,
            logged = emptyList(),
        )
    }
}

private fun formatRuleLabel(rule: ProgressionRule): String = when (rule) {
    is ProgressionRule.Linear -> "LINEAR +${trimNumber(rule.incrementKg)}"
    is ProgressionRule.GreyskullLp -> "GREYSKULL LP"
    is ProgressionRule.DoubleProgression -> "DOUBLE PROGRESSION ${rule.minReps}-${rule.maxReps}"
    is ProgressionRule.Timed -> "TIMED +${rule.incrementSec}S"
    is ProgressionRule.Bodyweight -> "BODYWEIGHT TO ${rule.repCeiling}"
}

private fun formatSet(loadKg: Double?, reps: Int?, durationSec: Int?): String = when {
    durationSec != null -> "${durationSec}s"
    loadKg != null && reps != null -> "${trimNumber(loadKg)} kg x $reps"
    reps != null -> "x $reps"
    else -> ""
}

private fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** Minutes and seconds, which is the unit rest is counted in. */
private fun formatRest(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/**
 * Whole minutes. The seconds are on the logger's clock while it runs; once the
 * session is over, nobody needs to know it was 42:18 rather than 42 minutes.
 */
private fun formatDuration(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "UNDER A MINUTE"
        minutes < 60 -> "$minutes MIN"
        else -> "${minutes / 60} H ${minutes % 60} MIN"
    }
}

/** "102.5 kg, 3 x 5". Sets first when there is no load, since that is all there is. */
private fun formatPrescription(p: Prescription): String {
    val work = if (p.durationSec != null) "${p.durationSec}s" else "${p.reps}"
    val volume = "${p.sets} x $work"
    return p.loadKg?.let { "${trimNumber(it)} kg, $volume" } ?: volume
}

/** Whole kilos print as whole kilos. The precision past that is not real. */
private fun trimNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

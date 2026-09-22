package app.tide

import app.tide.core.data.db.SetKind
import app.tide.core.data.db.WorkoutSetEntity
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
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(EMPTY_STATE)
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var sessionId: String? = null
    private var startedAt = now()
    private var exerciseName = exerciseId
    private var ruleLabel = ""
    private var targetSets = 1
    private var lastTime: String? = null
    private var loadKg: Double? = null
    private var reps = 0
    private var latestSets: List<WorkoutSetEntity> = emptyList()

    init {
        scope.launch {
            val exercise = repository.exerciseById(exerciseId)
            exerciseName = exercise?.name ?: exerciseId
            val rule = RuleCodec.decode(exercise?.progressionRule) ?: defaultRule
            ruleLabel = formatRuleLabel(rule)

            val prescription = repository.prescriptionFor(exerciseId)
            val last = repository.lastPerformance(exerciseId)
            targetSets = prescription?.sets ?: last?.sets ?: 1
            loadKg = prescription?.loadKg ?: last?.loadKg
            reps = prescription?.reps ?: last?.reps ?: 0
            lastTime = last?.let { formatSet(it.loadKg, it.reps, it.durationSec) }

            val id = repository.startSession()
            sessionId = id
            startedAt = repository.observeActiveSession().first()?.startedAt ?: now()
            render()

            repository.observeSets(id).collect { sets ->
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

    fun onLogSet() {
        val id = sessionId ?: return
        val currentLoad = loadKg
        val currentReps = reps
        scope.launch {
            repository.logSet(id, exerciseId, loadKg = currentLoad, reps = currentReps)
        }
    }

    private fun render() {
        val (logged, nextSetNumber) = buildLogged(latestSets)
        _state.value = SessionUiState(
            exerciseName = exerciseName,
            setNumber = nextSetNumber,
            targetSets = targetSets,
            ruleLabel = ruleLabel,
            loadKg = loadKg?.let(::trimNumber) ?: "",
            reps = reps.toString(),
            lastTime = lastTime,
            elapsed = formatElapsed(now() - startedAt),
            restRemaining = null,
            logged = logged,
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
            targetSets = 1,
            ruleLabel = "",
            loadKg = "",
            reps = "",
            lastTime = null,
            elapsed = "00:00:00",
            restRemaining = null,
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

/** Whole kilos print as whole kilos. The precision past that is not real. */
private fun trimNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

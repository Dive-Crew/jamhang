package pleos.jamhang.session

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import pleos.jamhang.data.Cabin
import pleos.jamhang.data.DiveLog
import pleos.jamhang.data.DiveLogStore
import pleos.jamhang.data.EndType
import pleos.jamhang.data.UnlockConfig
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class PauseReason { AUDIO, DOOR, USER }

/** 진행 중인 잠항. 종료 시각(endAt)은 연장(5분 더)으로만 바뀐다 (정책 보드 3). */
data class ActiveSession(
    val id: Long,
    val startedAt: Long,
    val endAt: Long,
    val plannedMin: Int,
    val cabin: Cabin,
    val memo: String = "",
    val breathGuide: Boolean = false,
    /** 호흡 박자 기준 시각: 여기서부터 4초 들숨 · 4초 날숨. 화면 점과 숨소리가 같은 값을 쓴다 */
    val breathStartedAt: Long = 0,
    val soundOn: Boolean = true,
    val volume: Float = 0.6f,
    /** 잠시 멈춤 누적 (현재 멈춤 구간 제외) */
    val pausedMs: Long = 0,
    /** 멈춘 시각. null = 쉬는 중 */
    val pausedSince: Long? = null,
    val pauseReason: PauseReason? = null,
    /** 멈춤 원인이 사라졌고 사용자의 "이어서 쉬기"를 기다리는 중 (16) */
    val awaitingResume: Boolean = false,
    /** 1분 전 예고 음성을 이미 낸 종료 시각 */
    val warnedForEndAt: Long = 0,
) {
    val isPaused: Boolean get() = pausedSince != null
    fun restMs(now: Long): Long {
        val end = minOf(now, endAt)
        val currentPause = pausedSince?.let { (end - it).coerceAtLeast(0) } ?: 0
        return (end - startedAt - pausedMs - currentPause).coerceAtLeast(0)
    }
    fun remainingMs(now: Long) = (endAt - now).coerceAtLeast(0)
    fun isWarning(now: Long) = !isPaused && remainingMs(now) in 1..WARNING_MS

    companion object {
        const val WARNING_MS = 60_000L
        const val EXTEND_MS = 5 * 60_000L
    }
}

data class DiveResult(
    val log: DiveLog,
    /** 저장 동의 없이 쉬었거나 1분 미만이라 기록하지 않음 (07-3) */
    val saved: Boolean,
    /** 같은 날 두 번째 이후 (07-2): 수심 변화 표현 없음 */
    val secondToday: Boolean,
    val totalDepth: Int,
)

sealed interface SessionState {
    data object Idle : SessionState
    data class Active(val session: ActiveSession) : SessionState
    data class Ended(val result: DiveResult) : SessionState
}

/**
 * 잠항 상태 머신. 앱 프로세스에 하나 (AppContainer 소유), DiveService가 시간·차량·오디오 이벤트를 넣는다.
 * 진행 중 세션은 SharedPreferences에 즉시 저장 → 프로세스 재생성에도 복원.
 */
class DiveSessionController(context: Context, private val logs: DiveLogStore) {

    private val prefs: SharedPreferences = context.getSharedPreferences("dive_session", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<SessionState>(restore()?.let { SessionState.Active(it) } ?: SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _consent = MutableStateFlow(prefs.getBoolean(KEY_CONSENT, true))
    /** 기록 저장 동의 (09). 프로필 화면 전까지는 테스트 메뉴에서만 바꾼다 */
    val consent: StateFlow<Boolean> = _consent.asStateFlow()

    var clock: () -> Long = System::currentTimeMillis

    /** 게스트 프로필이면 true → 기록하지 않음 (11). AppContainer가 연결 */
    var isGuest: () -> Boolean = { false }

    val active: ActiveSession? get() = (_state.value as? SessionState.Active)?.session

    fun start(plannedMin: Int, cabin: Cabin, memo: String, breathGuide: Boolean) {
        val now = clock()
        set(ActiveSession(
            id = now, startedAt = now, endAt = now + plannedMin * 60_000L,
            plannedMin = plannedMin, cabin = cabin, memo = memo, breathGuide = breathGuide,
            breathStartedAt = now,
        ))
    }

    /** 1초마다 호출. 종료 시각 도달 → 결과 */
    fun tick() {
        val s = active ?: return
        val now = clock()
        if (now >= s.endAt) finish(if (s.isPaused) EndType.PAUSED_END else EndType.COMPLETED)
    }

    fun pause(reason: PauseReason) {
        val s = active ?: return
        if (s.isPaused) {
            // 직접 멈춘 사이 문이 열리면 원인을 문으로 바꿔 "이어서 쉬기"를 막는다
            if (s.awaitingResume || (s.pauseReason == PauseReason.USER && reason != PauseReason.USER)) update { it.copy(awaitingResume = false, pauseReason = reason) }
            return
        }
        update { it.copy(pausedSince = clock(), pauseReason = reason, awaitingResume = false) }
    }

    /** 멈춤 원인 해소 (오디오 포커스 복귀, 문 닫힘). 자동 재개 X → "이어서 쉴까요?" */
    fun pauseCauseCleared(reason: PauseReason) {
        val s = active ?: return
        if (s.isPaused && s.pauseReason == reason) update { it.copy(awaitingResume = true) }
    }

    fun resume() {
        val s = active ?: return
        val since = s.pausedSince ?: return
        val now = clock()
        // 이어서 쉴 때 호흡은 들숨부터 다시 시작
        update { it.copy(pausedMs = it.pausedMs + (now - since), pausedSince = null, pauseReason = null, awaitingResume = false, breathStartedAt = now) }
    }

    fun extend() = update { it.copy(endAt = it.endAt + ActiveSession.EXTEND_MS) }

    fun setSoundOn(on: Boolean) = update { it.copy(soundOn = on) }
    fun setVolume(v: Float) = update { it.copy(volume = v.coerceIn(0f, 1f), soundOn = true) }
    fun setCabin(c: Cabin) = update { it.copy(cabin = c) }
    fun setBreathGuide(on: Boolean) = update { it.copy(breathGuide = on, breathStartedAt = clock()) }
    fun markWarned() = update { it.copy(warnedForEndAt = it.endAt) }

    fun finish(type: EndType) {
        val s = active ?: return
        val now = minOf(clock(), s.endAt)
        val closed = s.pausedSince?.let { s.copy(pausedMs = s.pausedMs + (now - it), pausedSince = null) } ?: s
        val restSec = (closed.restMs(now) / 1000).toInt()
        val date = Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()

        val saved = _consent.value && !isGuest() && restSec >= UnlockConfig.MIN_RECORD_SEC
        val secondToday = saved && logs.countOn(date) > 0
        val credited = saved && restSec >= UnlockConfig.MIN_CREDIT_SEC && !logs.hasCreditOn(date)
        val log = DiveLog(
            id = s.id, startedAt = s.startedAt, endedAt = now, plannedMin = s.plannedMin, cabin = s.cabin,
            restSec = restSec, endType = type, credited = credited, memo = s.memo,
        )
        if (saved) logs.add(log)
        clearPersisted()
        _state.value = SessionState.Ended(DiveResult(log, saved, secondToday, logs.totalDepth))
    }

    fun acknowledgeResult() {
        if (_state.value is SessionState.Ended) _state.value = SessionState.Idle
    }

    fun setConsent(on: Boolean) {
        _consent.value = on
        prefs.edit { putBoolean(KEY_CONSENT, on) }
    }

    /** 테스트 메뉴 · 프로토타입 전용 "시간 건너뛰기": 종료 1분 5초 전으로 */
    fun skipToWarning() = update {
        val shift = it.endAt - clock() - 65_000L
        if (shift <= 0) it else it.copy(startedAt = it.startedAt - shift, endAt = it.endAt - shift)
    }

    fun todayCount(today: LocalDate = LocalDate.now()) = logs.countOn(today)

    private inline fun update(block: (ActiveSession) -> ActiveSession) {
        val s = active ?: return
        set(block(s))
    }

    private fun set(s: ActiveSession) {
        _state.value = SessionState.Active(s)
        persist(s)
    }

    private fun persist(s: ActiveSession) = prefs.edit(commit = true) {
        putString(KEY_ACTIVE, JSONObject()
            .put("id", s.id).put("startedAt", s.startedAt).put("endAt", s.endAt)
            .put("plannedMin", s.plannedMin).put("cabin", s.cabin.name).put("memo", s.memo)
            .put("breath", s.breathGuide).put("soundOn", s.soundOn).put("volume", s.volume.toDouble())
            .put("pausedMs", s.pausedMs).put("pausedSince", s.pausedSince ?: -1L)
            .put("pauseReason", s.pauseReason?.name ?: "").put("awaiting", s.awaitingResume)
            .put("warned", s.warnedForEndAt).put("breathAt", s.breathStartedAt)
            .toString())
    }

    private fun clearPersisted() = prefs.edit(commit = true) { remove(KEY_ACTIVE) }

    private fun restore(): ActiveSession? = runCatching {
        val j = JSONObject(prefs.getString(KEY_ACTIVE, null) ?: return null)
        ActiveSession(
            id = j.getLong("id"), startedAt = j.getLong("startedAt"), endAt = j.getLong("endAt"),
            plannedMin = j.getInt("plannedMin"), cabin = Cabin.valueOf(j.getString("cabin")),
            memo = j.optString("memo"), breathGuide = j.optBoolean("breath"),
            soundOn = j.optBoolean("soundOn", true), volume = j.optDouble("volume", 0.6).toFloat(),
            pausedMs = j.optLong("pausedMs"),
            pausedSince = j.optLong("pausedSince", -1L).takeIf { it >= 0 },
            pauseReason = j.optString("pauseReason").takeIf { it.isNotEmpty() }?.let { PauseReason.valueOf(it) },
            awaitingResume = j.optBoolean("awaiting"),
            warnedForEndAt = j.optLong("warned"),
            breathStartedAt = j.optLong("breathAt", j.getLong("startedAt")),
        )
    }.getOrNull()

    private companion object {
        const val KEY_ACTIVE = "active"
        const val KEY_CONSENT = "consent"
    }
}

package pleos.jamhang.session

/**
 * 호흡 안내 약 1분 (Figma 01 · 호흡 안내 B00–B05, 11/12).
 *
 * B01 시작 4초 → B02 들숨 5초(원 확장) ↔ B03 날숨 5초(원 축소) × 5회 → B04 완료 2.5초 → 조용한 휴식.
 * 화면(원·문구), 숨소리, 안내 음성이 모두 [ActiveSession.breathStartedAt] 기준으로 이 표를 읽는다.
 */
object BreathScript {
    const val INTRO_MS = 4_000L
    const val HALF_MS = 5_000L
    const val CYCLES = 5
    /** 들숨·날숨 안내가 끝나는 시점 (54초) */
    const val GUIDE_END_MS = INTRO_MS + HALF_MS * 2 * CYCLES
    const val DONE_MS = 2_500L
    /** 이 시점이 지나면 안내를 끄고 조용한 휴식으로 */
    const val TOTAL_MS = GUIDE_END_MS + DONE_MS

    enum class Phase { INTRO, INHALE, EXHALE, DONE, OVER }

    fun phaseAt(elapsed: Long): Phase = when {
        elapsed < INTRO_MS -> Phase.INTRO
        elapsed < GUIDE_END_MS -> if (((elapsed - INTRO_MS) / HALF_MS) % 2 == 0L) Phase.INHALE else Phase.EXHALE
        elapsed < TOTAL_MS -> Phase.DONE
        else -> Phase.OVER
    }

    /** 들숨/날숨 안에서의 진행도 0..1 */
    fun progressAt(elapsed: Long): Float =
        if (elapsed < INTRO_MS || elapsed >= GUIDE_END_MS) 0f
        else ((elapsed - INTRO_MS) % HALF_MS) / HALF_MS.toFloat()

    /** 들숨·날숨이 시작하는 시각들 (짝수 번째 = 들숨) */
    val boundaries: List<Long> = (0 until CYCLES * 2).map { INTRO_MS + it * HALF_MS }
}

package pleos.jamhang.data

import androidx.annotation.DrawableRes
import pleos.jamhang.R

/** 선실 = 휴식 배경·소리. 시간과 독립 (정책 보드 1) */
enum class Cabin(
    val title: String,
    val subtitle: String,
    @param:DrawableRes val thumb: Int,
    /** 23 항해 일지 날 카드 배너 (Figma "풍경 원본" 1536px에서 자름) */
    @param:DrawableRes val banner: Int,
) {
    LIGHT_SEA("빛의 바다", "잔잔한 물결 소리", R.drawable.cabin_light_sea, R.drawable.banner_light_sea),
    SEAWEED("해초 숲", "해류", R.drawable.cabin_seaweed, R.drawable.banner_seaweed),
    OLD_GATE("오래된 문", "깊은 물", R.drawable.cabin_old_gate, R.drawable.banner_old_gate),
    PALACE("용궁", "고요", R.drawable.cabin_palace, R.drawable.banner_palace),
}

/**
 * 장소 해금 수치 (m). 정책 보드 2-1 v9 값 + 빛의 바다는 기본 선실이라 0m (§7.0 제안).
 * 이용 빈도 테스트 후 여기만 바꾸면 된다.
 */
object UnlockConfig {
    val depthToUnlock: Map<Cabin, Int> = mapOf(
        Cabin.LIGHT_SEA to 0,
        Cabin.SEAWEED to 3_000,
        Cabin.OLD_GATE to 6_000,
        Cabin.PALACE to 9_000,
    )

    /** 하루 첫 적립 +100m */
    const val DEPTH_PER_DAY = 100

    /** 기록 조건: 휴식 1분 이상 [임시값] */
    const val MIN_RECORD_SEC = 60

    /** 적립 기준: 휴식 5분 이상 [제안, 임시값] */
    const val MIN_CREDIT_SEC = 5 * 60

    fun isUnlocked(cabin: Cabin, totalDepth: Int) = totalDepth >= (depthToUnlock[cabin] ?: 0)
}

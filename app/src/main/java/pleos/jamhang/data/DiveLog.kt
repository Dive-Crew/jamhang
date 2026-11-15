package pleos.jamhang.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class EndType(val label: String?) {
    COMPLETED(null),
    EARLY("일찍 마침"),
    PAUSED_END("멈춤 중 종료"),
    AUTO("자동 마침"),
}

/** 항해 일지 1행 = 휴식 1번 (같은 날 여러 번도 각각) */
data class DiveLog(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long,
    val plannedMin: Int,
    val cabin: Cabin,
    /** 실제로 쉰 시간 = 경과 - 잠시 멈춤 */
    val restSec: Int,
    val endType: EndType,
    /** 이 휴식으로 +100m 적립했는지 (하루 1번) */
    val credited: Boolean,
    val memo: String = "",
) {
    /** 날짜 귀속 = 세션을 시작한 날 (차량 현지 시간) */
    val date: LocalDate get() = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val restMin: Int get() = restSec / 60
}

/** 홈 지층 패널의 한 겹 = 하루 */
data class StrataLayer(
    val date: LocalDate,
    val cabin: Cabin,
    val count: Int,
    val totalMin: Int,
    val lastEndType: EndType,
    val credited: Boolean,
) {
    val summary: String
        // Figma 11/12: 여러 번 쉰 날도 "2회, 총" 없이 합계 시간만
        get() = when {
            count > 1 -> "${totalMin}분"
            lastEndType.label != null -> "${totalMin}분 (${lastEndType.label})"
            else -> "${totalMin}분"
        }
}

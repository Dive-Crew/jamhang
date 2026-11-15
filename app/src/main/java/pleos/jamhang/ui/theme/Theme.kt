package pleos.jamhang.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pleos.jamhang.R

/** Figma 변수 (Pleos Color, Dark) — docs/DESIGN.md §7.2 */
object JhColor {
    val Basic00 = Color(0xFF131417)
    val Basic100 = Color(0xFF313236)
    val BackgroundPrimary = Color(0xFF1D1E21)
    val ButtonBasic = Color(0x0DFFFFFF)
    val ButtonFilled = Color(0xFFE0E1E6)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xD6FFFFFF)
    val TextTertiary = Color(0xA3FFFFFF)
    val TextQuaternary = Color(0x52FFFFFF)
    val TextOnFilled = Color(0xFF131417)
    val DividerSecondary = Color(0x1AFFFFFF)
    val DividerTertiary = Color(0x33FFFFFF)
    val Dim = Color(0xD6131417)
    val Mint = Color(0xFF7FD8C9)
    val Amber = Color(0xFFF2B880)
    val Steel = Color(0xFF9AABC4)

    /** 20 연장 팝업 뒤 배경 (Figma 20: 거의 검정 단색) */
    val BgSolidDark = Color(0xFF08090B)
    /** 20 팝업 안 "주차 상태" 박스 */
    val PopupInset = Color(0xFF3C3C40)
}

/**
 * 화면별 세로 그라데이션. Figma 렌더 이미지에서 빈 영역을 위→아래로 뽑은 값 그대로 (위치 = 높이 비율).
 * 화면마다 위쪽 밝기와 아래쪽 어둡기가 달라서 공용 하나로는 맞지 않는다.
 */
enum class JhBackground(vararg val stops: Pair<Float, Long>) {
    /** 01 홈 */
    Home(0f to 0xFF191D23, .13f to 0xFF171B22, .26f to 0xFF15191D, .39f to 0xFF121519, .52f to 0xFF0F1216,
        .65f to 0xFF0E1013, .78f to 0xFF0D0E12, .91f to 0xFF0A0C0C, 1f to 0xFF070809),
    /** 02 잠항 준비 — 가장 밝고 푸른 슬레이트 */
    Prep(0f to 0xFF2A323E, .08f to 0xFF28303B, .16f to 0xFF252D37, .26f to 0xFF212832, .39f to 0xFF1C232B,
        .52f to 0xFF181E27, .65f to 0xFF161A21, .78f to 0xFF12151A, .91f to 0xFF0C0E12, 1f to 0xFF080B0D),
    /** 03 해치 — 가장 어둡다 */
    Hatch(0f to 0xFF13161B, .13f to 0xFF111418, .26f to 0xFF0E1014, .39f to 0xFF0B0D10, .52f to 0xFF0B0C10,
        .65f to 0xFF0A0C0D, .78f to 0xFF090B0D, .91f to 0xFF070709, 1f to 0xFF040507),
    /** 07·08 결과, 지층 보기 */
    Result(0f to 0xFF282F38, .13f to 0xFF252C34, .26f to 0xFF21272E, .39f to 0xFF1C2127, .52f to 0xFF191D22,
        .65f to 0xFF15181C, .78f to 0xFF111418, .91f to 0xFF0C0E10, 1f to 0xFF090A0C);

    val brush: Brush get() = Brush.verticalGradient(*stops.map { (p, c) -> p to Color(c) }.toTypedArray())
}

/**
 * 19 1분 전 예고: 가운데가 밝은 타원형 그라데이션 (가로로 넓고 세로로 좁음).
 * Figma 19 렌더 기준 중심 (850, 680), 가로 반경 ≈ 900, 세로 반경 ≈ 640.
 * 아래쪽은 Figma에서 더 빨리 어두워져서 하단 [BOTTOM_FADE_FROM]부터 어두운 막을 한 겹 더 깐다.
 */
object OneMinuteBackground {
    val stops = arrayOf(
        0f to Color(0xFF3A404B), 0.2f to Color(0xFF353C47), 0.45f to Color(0xFF282F39),
        0.7f to Color(0xFF1C2128), 1f to Color(0xFF111419),
    )
    const val CENTER_X = 850f
    const val CENTER_Y = 680f
    const val RADIUS_X = 900f
    const val RADIUS_Y = 640f
    const val BOTTOM_FADE_FROM = 900f
    val BottomFade = Color(0xCC0E1115)
}

@OptIn(ExperimentalTextApi::class)
private fun asta(weight: Int) = Font(
    R.font.asta_sans, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val AstaSans = FontFamily(asta(300), asta(400), asta(500), asta(600), asta(700), asta(800))

/** Figma 타이포 토큰 — §7.3 */
object JhType {
    private fun s(size: Int, line: Int, w: Int, track: Float = 0f) = TextStyle(
        fontFamily = AstaSans, fontSize = size.sp, lineHeight = line.sp,
        fontWeight = FontWeight(w), letterSpacing = track.sp, color = JhColor.TextPrimary,
    )
    val Countdown = s(112, 134, 300, -3f)
    val HeadlineMedium = s(56, 72, 800)
    val HeadlineSmall = s(48, 64, 800)
    val TitleLarge = s(40, 52, 700)
    val TitleMedium = s(36, 44, 700)
    val TitleSmall = s(32, 40, 700)
    val BodyLarge = s(30, 38, 700)
    val BodyLargeRegular = s(30, 38, 400)
    val BodyMedium = s(28, 36, 700)
    val BodyMediumRegular = s(28, 36, 400)
    /** 최소 캡션 24 (PDF 피드백 9: 20sp 이상) */
    val Caption = s(24, 32, 500)
}

/** Figma 앱 창 기준 크기. 이 캔버스를 실제 창에 비율 유지로 맞춘다 (14.6″, 12.9″ 스케일) */
const val DESIGN_W = 1700f
const val DESIGN_H = 1184f

@Composable
fun JamhangTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = JhColor.BackgroundPrimary,
            surface = JhColor.Basic100,
            primary = JhColor.ButtonFilled,
            onPrimary = JhColor.TextOnFilled,
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val base = LocalDensity.current
            val wPx = with(base) { maxWidth.toPx() }
            val hPx = with(base) { maxHeight.toPx() }
            // 1 디자인 px = scale 실제 px
            val scale = minOf(wPx / DESIGN_W, hPx / DESIGN_H)
            CompositionLocalProvider(LocalDensity provides Density(scale, fontScale = 1f)) {
                Box(Modifier.requiredSize(DESIGN_W.dp, DESIGN_H.dp)) { content() }
            }
        }
    }
}

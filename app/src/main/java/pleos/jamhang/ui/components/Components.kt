package pleos.jamhang.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pleos.jamhang.ui.theme.JhBackground
import pleos.jamhang.ui.theme.JhColor
import pleos.jamhang.ui.theme.JhType

enum class JhButtonStyle { Filled, Basic }
enum class JhButtonSize(val height: Dp, val padH: Dp, val radius: Dp, val minW: Dp) {
    Large(100.dp, 40.dp, 32.dp, 140.dp),
    Medium(80.dp, 32.dp, 24.dp, 120.dp),
}

/** ivi_basic_button: Filled(=Secondary) 주요 CTA / Basic(=Primary) 보조 */
@Composable
fun JhButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: JhButtonStyle = JhButtonStyle.Basic,
    size: JhButtonSize = JhButtonSize.Large,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(size.radius)
    val bg = when (style) {
        JhButtonStyle.Filled -> if (pressed) Color(0xFFC9CAD0) else JhColor.ButtonFilled
        JhButtonStyle.Basic -> if (pressed) Color(0x1FFFFFFF) else JhColor.ButtonBasic
    }
    val fg = if (style == JhButtonStyle.Filled) JhColor.TextOnFilled else JhColor.TextPrimary
    Box(
        modifier
            .height(size.height)
            .widthIn(min = size.minW)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(bg)
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = size.padH),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (size == JhButtonSize.Large) JhType.BodyLarge else JhType.BodyMedium, color = fg, maxLines = 1)
    }
}

/** 카드: 라운드 24, 흰색 5%, 1px 테두리 */
@Composable
fun JhCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.clip(shape).background(JhColor.ButtonBasic).border(1.dp, JhColor.DividerSecondary, shape),
        content = content,
    )
}

@Composable
fun IconBox(icon: ImageVector, tint: Color = JhColor.TextPrimary) {
    Box(
        Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(JhColor.ButtonBasic)
            .border(1.dp, JhColor.DividerSecondary, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(48.dp)) }
}

/** ivi_title (Depth): ‹ + 제목 */
@Composable
fun JhHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().height(80.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(pleos.jamhang.ui.theme.PlIcons.ArrowLeft, "뒤로", tint = JhColor.TextPrimary, modifier = Modifier.size(40.dp)) }
        Spacer(Modifier.width(8.dp))
        Text(title, style = JhType.TitleLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

data class Stat(val label: String, val value: String)

/** 통계 3열 + 세로 디바이더 */
@Composable
fun StatRow(stats: List<Stat>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(148.dp), verticalAlignment = Alignment.CenterVertically) {
        stats.forEachIndexed { i, s ->
            if (i > 0) Box(Modifier.width(1.dp).height(96.dp).background(JhColor.DividerSecondary))
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(start = if (i == 0) 0.dp else 32.dp, top = 28.dp),
            ) {
                Text(s.label, style = JhType.Caption.copy(fontSize = JhType.BodyMediumRegular.fontSize), color = JhColor.TextTertiary)
                Spacer(Modifier.height(8.dp))
                Text(s.value, style = JhType.TitleLarge)
            }
        }
    }
}

/** 배지 ("부상 완료", "자동 부상") */
@Composable
fun JhBadge(text: String, color: Color = JhColor.TextSecondary) {
    Box(
        Modifier.clip(RoundedCornerShape(28.dp)).background(Color(0x1FFFFFFF))
            .border(1.dp, JhColor.DividerTertiary, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) { Text(text, style = JhType.Caption.copy(fontWeight = JhType.BodyMedium.fontWeight), color = color) }
}

/** ivi_system_popup (Medium): 딤 + 가운데 카드 */
@Composable
fun JhPopup(
    onDismiss: (() -> Unit)?,
    width: Dp = 812.dp,
    scrim: Color = JhColor.Dim,
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(48.dp),
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(scrim)
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.width(width).shadow(32.dp, RoundedCornerShape(40.dp))
                .clip(RoundedCornerShape(40.dp)).background(JhColor.Basic100)
                .border(1.dp, JhColor.DividerSecondary, RoundedCornerShape(40.dp))
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(padding),
        ) { content() }
    }
}

@Composable
fun PopupTwoButtons(
    title: String,
    primary: String, onPrimary: () -> Unit,
    secondary: String, onSecondary: () -> Unit,
    primaryFirst: Boolean = true,
    body: @Composable () -> Unit = {},
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = JhType.TitleMedium, textAlign = TextAlign.Center)
        body()
        Spacer(Modifier.height(40.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (primaryFirst) {
                JhButton(primary, onPrimary, Modifier.weight(1f), style = JhButtonStyle.Filled)
                JhButton(secondary, onSecondary, Modifier.weight(1f))
            } else {
                JhButton(secondary, onSecondary, Modifier.weight(1f))
                JhButton(primary, onPrimary, Modifier.weight(1f), style = JhButtonStyle.Filled)
            }
        }
    }
}

/** ivi_toast_popup */
@Composable
fun BoxScope.JhToast(text: String?) {
    if (text == null) return
    Box(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color(0xFFE6E7EC))
            .padding(horizontal = 40.dp, vertical = 20.dp),
    ) { Text(text, style = JhType.BodyMedium, color = JhColor.TextOnFilled) }
}

/** 배경: 은은한 앰비언트 빛 두 개 + 입자 (Figma "ambient light", "particles") */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, bg: JhBackground = JhBackground.Home) {
    val gradient = remember(bg) { bg.brush }
    Canvas(modifier.fillMaxSize()) {
        drawRect(gradient)
        drawOval(
            Brush.radialGradient(listOf(Color(0x14607890), Color.Transparent), center = Offset(1275f.dp.toPx(), 148f.dp.toPx()), radius = 510.dp.toPx()),
            topLeft = Offset(765.dp.toPx(), (-178).dp.toPx()), size = Size(1020.dp.toPx(), 651.dp.toPx()),
        )
        drawOval(
            Brush.radialGradient(listOf(Color(0x10406A78), Color.Transparent), center = Offset(255.dp.toPx(), 947.dp.toPx()), radius = 425.dp.toPx()),
            topLeft = Offset((-170).dp.toPx(), 651.dp.toPx()), size = Size(850.dp.toPx(), 592.dp.toPx()),
        )
        PARTICLES.forEach { (x, y, s) ->
            drawCircle(Color(0x29FFFFFF), radius = (s / 2f).dp.toPx(), center = Offset(x.dp.toPx(), y.dp.toPx()))
        }
    }
}

/** Figma particles 위치 (01 홈 기준) */
private val PARTICLES = listOf(
    Triple(953f, 137f, 4f), Triple(861f, 93f, 4f), Triple(698f, 337f, 4f), Triple(130f, 590f, 4f),
    Triple(269f, 664f, 6f), Triple(85f, 1112f, 4f), Triple(755f, 1132f, 4f), Triple(936f, 795f, 4f),
    Triple(1676f, 528f, 4f), Triple(562f, 182f, 4f), Triple(595f, 94f, 4f), Triple(1503f, 557f, 4f),
    Triple(683f, 899f, 4f), Triple(381f, 533f, 4f), Triple(552f, 519f, 6f), Triple(1425f, 534f, 4f),
    Triple(402f, 880f, 4f), Triple(1303f, 1016f, 4f), Triple(1463f, 885f, 6f), Triple(41f, 383f, 4f),
    Triple(1626f, 8f, 4f), Triple(1483f, 696f, 4f), Triple(1348f, 1061f, 4f), Triple(263f, 837f, 4f),
    Triple(1478f, 272f, 4f), Triple(64f, 97f, 4f), Triple(1412f, 998f, 4f), Triple(88f, 331f, 4f),
    Triple(605f, 273f, 4f), Triple(232f, 195f, 6f), Triple(147f, 478f, 4f), Triple(1256f, 400f, 4f),
    Triple(1087f, 317f, 6f), Triple(1498f, 98f, 4f), Triple(517f, 35f, 4f), Triple(641f, 639f, 4f),
)

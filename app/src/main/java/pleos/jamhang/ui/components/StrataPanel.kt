package pleos.jamhang.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import pleos.jamhang.R
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import pleos.jamhang.data.StrataLayer
import pleos.jamhang.ui.theme.JhColor
import pleos.jamhang.ui.theme.JhType
import java.time.LocalDate
import java.text.NumberFormat

private const val ROWS = 8

/** 지층이 비어 보일 때 (Figma 10 새 프로필 · 11 게스트 · 27 저장하지 않는 프로필) */
enum class PanelEmpty { NoLayers, Guest, NotSaving }

/**
 * 나의 지층 패널 (600×1088). 하루 1겹, 최신이 위.
 * @param newLayerToday true면 오늘 겹을 "+ 오늘 …"으로 강조하고 위에서 내려앉는 +1 모션
 */
@Composable
fun StrataPanel(
    layers: List<StrataLayer>,
    totalDepth: Int,
    modifier: Modifier = Modifier,
    newLayerToday: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** 23 항해 일지: 고른 날의 행을 밝히고 잠수함을 그 행으로 옮긴다 */
    selectedIndex: Int? = null,
    onRowClick: ((Int) -> Unit)? = null,
    emptyState: PanelEmpty? = null,
    ownerName: String? = null,
    onEmptyAction: () -> Unit = {},
) {
    val today = LocalDate.now()
    val shape = RoundedCornerShape(40.dp)
    val drop = remember { Animatable(if (newLayerToday) 0f else 1f) }
    LaunchedEffect(newLayerToday) {
        if (newLayerToday) drop.animateTo(1f, tween(1200, delayMillis = 300, easing = FastOutSlowInEasing))
    }
    Box(
        modifier.size(600.dp, 1088.dp).clip(shape)
            .border(1.dp, JhColor.DividerSecondary, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        // Figma 11/12 개편: 제목·겹 수는 위로, 단면은 가로 전체(216~920), 행은 "날짜 · 시간"만 (88 간격)
        Image(painterResource(R.drawable.bg_strata_panel), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (emptyState != null) {
            EmptyPanel(emptyState, ownerName, onEmptyAction)
            return@Box
        }
        val label = JhType.Caption.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal)
        Text("항해 일지", style = label, color = JhColor.TextTertiary, modifier = Modifier.offset(36.dp, 36.dp))
        Text(
            "나의 지층", style = JhType.BodyLarge.copy(fontWeight = FontWeight.SemiBold), color = JhColor.TextPrimary,
            modifier = Modifier.offset(36.dp, 68.dp),
        )
        Text("최근 기록", style = label, color = JhColor.TextTertiary, modifier = Modifier.offset(36.dp, 166.dp))
        Text(
            "${layers.size}겹", style = JhType.BodyLargeRegular, color = JhColor.TextPrimary, textAlign = TextAlign.End,
            modifier = Modifier.offset(400.dp, 84.dp).width(164.dp),
        )
        // Figma: 행 배경 없이 바다 그림 위에 물결 선만 (수면 216 + 행 경계)
        Canvas(Modifier.fillMaxSize()) {
            val rows = minOf(ROWS, layers.size)
            for (k in 0 until maxOf(rows, 1)) {
                val y0 = (216 + 88 * k).dp.toPx()
                val amp = (if (k == 0) 5 else 3 + (k % 3)).dp.toPx()
                val phase = k * 1.7f
                val path = Path().apply {
                    var x = 0f
                    moveTo(0f, y0 + amp * kotlin.math.sin(phase))
                    while (x <= size.width) {
                        lineTo(x, y0 + amp * kotlin.math.sin(phase + x / size.width * 2f * Math.PI.toFloat() * 1.3f))
                        x += 6f
                    }
                }
                drawPath(
                    path, Color.White.copy(alpha = if (k == 0) 0.45f else 0.16f),
                    style = Stroke(if (k == 0) 2.dp.toPx() else 1.5.dp.toPx()),
                )
            }
        }
        val sel = selectedIndex?.takeIf { it in 0 until minOf(ROWS, layers.size) }
        // 시간 글자가 길면("7분 (일찍 마침)") 잠수함을 날짜 쪽으로 비켜 겹치지 않게
        val longSummary = sel != null && layers[sel].summary.length > 5
        val subX by animateDpAsState(if (sel == null) 425.dp else if (longSummary) 190.dp else 330.dp, tween(500), label = "subX")
        val subY by animateDpAsState(if (sel != null) 216.dp + 88.dp * sel + 22.dp else 164.dp, tween(500), label = "subY")
        if (sel != null) SplashBand(sel, subX + 42.dp)
        Image(
            painterResource(R.drawable.ic_submarine), "잠수함",
            Modifier.offset(subX, subY).size(84.dp, 45.dp),
        )
        Column(Modifier.offset(28.dp, 216.dp).width(544.dp)) {
            layers.take(ROWS).forEachIndexed { i, layer ->
                val isToday = layer.date == today
                val animated = isToday && newLayerToday && i == 0
                Row(
                    Modifier.fillMaxWidth().height(88.dp)
                        .graphicsLayer {
                            if (animated) {
                                alpha = drop.value
                                translationY = (1f - drop.value) * -120f
                            }
                        }
                        .then(if (animated) Modifier.background(Color(0x1FFFFFFF)) else Modifier)
                        .then(if (onRowClick != null) Modifier.clickable { onRowClick(i) } else Modifier)
                        .padding(start = if (i == sel) 0.dp else 18.dp, end = if (i == sel) 8.dp else 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (i == sel) {
                        SelectedMarker()
                        Spacer(Modifier.width(12.dp))
                    }
                    val top = if (sel != null) i == sel else i == 0
                    Text(
                        if (isToday) "오늘" else "${layer.date.monthValue}/${layer.date.dayOfMonth}",
                        style = JhType.Caption.copy(fontWeight = if (top) FontWeight.Bold else FontWeight.Normal),
                        color = if (top) JhColor.TextPrimary else JhColor.TextTertiary,
                    )
                    // 가장 최근 겹 표시 점 (Figma "latest layer marker")
                    if (i == 0 && sel == null) {
                        Spacer(Modifier.width(22.dp))
                        Box(Modifier.size(6.dp).clip(CircleShape).background(JhColor.TextSecondary))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        layer.summary, style = JhType.Caption.copy(fontWeight = FontWeight.Normal),
                        color = if (sel == null || i == sel) JhColor.TextSecondary else JhColor.TextTertiary,
                    )
                    if (i == sel) {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Outlined.BookmarkBorder, null, tint = JhColor.TextSecondary, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

fun formatDepth(m: Int): String = NumberFormat.getIntegerInstance().format(m) + "m"

@Composable
private fun EmptyPanel(state: PanelEmpty, ownerName: String?, onAction: () -> Unit) {
    val cap = JhType.Caption.copy(fontWeight = FontWeight.Normal)
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.ic_submarine), "잠수함", Modifier.offset(254.dp, 48.dp).size(91.dp, 50.dp))
        if (state == PanelEmpty.NoLayers) {
            // 첫 겹이 쌓일 자리 (점선)
            Box(
                Modifier.offset(28.dp, 150.dp).size(544.dp, 88.dp)
                    .drawBehind {
                        drawRoundRect(
                            Color(0x66FFFFFF), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                            ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { Text("+ 첫 겹", style = cap, color = JhColor.TextSecondary) }
        }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state != PanelEmpty.NoLayers) {
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(Color(0x26FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Lock, null, tint = JhColor.TextPrimary, modifier = Modifier.size(36.dp)) }
                Spacer(Modifier.height(28.dp))
            }
            Text(
                when (state) {
                    PanelEmpty.NoLayers -> "아직 쌓인 지층이 없어요"
                    PanelEmpty.Guest -> "게스트는 기록을 남기지 않아요"
                    PanelEmpty.NotSaving -> "지층을 저장하지 않고 있어요"
                },
                style = JhType.BodyMedium, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when (state) {
                    PanelEmpty.NoLayers -> "휴식을 기록하면 이곳에 쌓여요"
                    PanelEmpty.Guest -> "기록을 남기려면 내 프로필로 바꿔 주세요."
                    PanelEmpty.NotSaving -> "프로필과 기록에서 지층 저장을 켜면 쌓여요."
                },
                style = cap, color = JhColor.TextTertiary, textAlign = TextAlign.Center,
            )
            if (state != PanelEmpty.NoLayers) {
                Spacer(Modifier.height(36.dp))
                Box(
                    Modifier.height(80.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x26FFFFFF))
                        .clickable(onClick = onAction).padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (state == PanelEmpty.Guest) "Pleos 프로필 바꾸기" else "지층 저장 켜기", style = JhType.Caption) }
            }
        }
        if (state == PanelEmpty.NoLayers) {
            Text(
                "${ownerName?.let { "${it}님의 " } ?: ""}지층 0겹 (0m)", style = cap, color = JhColor.TextTertiary,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 32.dp),
            )
        }
    }
}

/** 23 고른 행: 물결 띠 + 잠수함 아래로 번지는 물보라 (Figma 항해 일지) */
@Composable
private fun SplashBand(row: Int, subCenterX: androidx.compose.ui.unit.Dp) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "splash")
    val wave by t.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        androidx.compose.animation.core.infiniteRepeatable(tween(4000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "wave",
    )
    val ring by t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(tween(2400, easing = androidx.compose.animation.core.LinearEasing)),
        label = "ring",
    )
    Canvas(Modifier.fillMaxSize()) {
        val top = (216 + 88 * row).dp.toPx() - 6.dp.toPx()
        val bottom = top + 100.dp.toPx()
        val amp = 6.dp.toPx()
        fun edge(y0: Float, ph: Float, x: Float) = y0 + amp * kotlin.math.sin(ph + x / size.width * 2f * Math.PI.toFloat())
        val band = Path().apply {
            moveTo(0f, edge(top, wave, 0f))
            var x = 0f
            while (x <= size.width) { lineTo(x, edge(top, wave, x)); x += 6f }
            x = size.width
            while (x >= 0f) { lineTo(x, edge(bottom, wave + 1.4f, x)); x -= 6f }
            close()
        }
        drawPath(band, Color.White.copy(alpha = 0.07f))
        for (y0 in listOf(top to wave, bottom to wave + 1.4f)) {
            val line = Path().apply {
                moveTo(0f, edge(y0.first, y0.second, 0f))
                var x = 0f
                while (x <= size.width) { lineTo(x, edge(y0.first, y0.second, x)); x += 6f }
            }
            drawPath(line, Color.White.copy(alpha = 0.32f), style = Stroke(1.5.dp.toPx()))
        }
        // 물보라: 잠수함 배 아래에서 번지는 납작한 타원 고리
        val cx = subCenterX.toPx()
        val cy = top + 6.dp.toPx() + 66.dp.toPx()
        for (k in 0 until 3) {
            val f = (ring + k / 3f) % 1f
            val w = (70 + 90 * f).dp.toPx()
            val h = w * 0.16f
            drawOval(
                Color.White.copy(alpha = 0.35f * (1f - f)),
                topLeft = androidx.compose.ui.geometry.Offset(cx - w / 2, cy - h / 2),
                size = androidx.compose.ui.geometry.Size(w, h),
                style = Stroke(1.5.dp.toPx()),
            )
        }
    }
}

/** 고른 행 왼쪽 표식: 괄호 + 과녁 */
@Composable
private fun SelectedMarker() {
    Canvas(Modifier.size(34.dp, 40.dp)) {
        val c = Color.White.copy(alpha = 0.7f)
        val sw = 1.5.dp.toPx()
        val bx = 4.dp.toPx()
        drawLine(c, androidx.compose.ui.geometry.Offset(bx + 6.dp.toPx(), 4.dp.toPx()), androidx.compose.ui.geometry.Offset(bx, 4.dp.toPx()), sw)
        drawLine(c, androidx.compose.ui.geometry.Offset(bx, 4.dp.toPx()), androidx.compose.ui.geometry.Offset(bx, size.height - 4.dp.toPx()), sw)
        drawLine(c, androidx.compose.ui.geometry.Offset(bx, size.height - 4.dp.toPx()), androidx.compose.ui.geometry.Offset(bx + 6.dp.toPx(), size.height - 4.dp.toPx()), sw)
        val center = androidx.compose.ui.geometry.Offset(size.width - 12.dp.toPx(), size.height / 2)
        drawCircle(Color.White, 9.dp.toPx(), center, style = Stroke(sw))
        drawCircle(Color.White, 4.dp.toPx(), center)
    }
}

package pleos.jamhang.ui.strata

import pleos.jamhang.R
import pleos.jamhang.ui.components.JhButtonStyle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pleos.jamhang.AppContainer
import pleos.jamhang.data.DiveLog
import pleos.jamhang.data.DiveLogStore
import pleos.jamhang.data.StrataLayer
import pleos.jamhang.ui.components.AmbientBackground
import pleos.jamhang.ui.components.JhButton
import pleos.jamhang.ui.components.JhHeader
import pleos.jamhang.ui.components.StrataPanel
import pleos.jamhang.ui.theme.JhBackground
import pleos.jamhang.ui.theme.JhColor
import pleos.jamhang.ui.theme.JhType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 최근 몇 일을 보여 주는지 (지층 패널 행 수와 같음) */
private const val RECENT_DAYS = 8

/**
 * 23 항해 일지 (하루 보기) · 23-M 저장된 기록 (Figma 11/12).
 * 왼쪽 지층 패널의 행을 누르면 그날로, [일지 모아보기]는 최근 기록 목록으로.
 */
@Composable
fun StrataScreen(c: AppContainer, onBack: () -> Unit, startAll: Boolean = false) {
    val logs by c.logs.logs.collectAsStateWithLifecycle()
    val layers = remember(logs) { DiveLogStore.layers(logs) }
    val depth = remember(logs) { DiveLogStore.depthOf(logs) }
    var selected by rememberSaveable { mutableStateOf(0) }
    var showAll by rememberSaveable { mutableStateOf(startAll) }
    val goBack = { if (showAll) showAll = false else onBack() }
    BackHandler(onBack = goBack)

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(bg = JhBackground.Result)
        StrataPanel(
            layers, depth, Modifier.offset(48.dp, 48.dp),
            selectedIndex = if (showAll) null else selected,
            onRowClick = { selected = it; showAll = false },
        )
        Column(Modifier.offset(714.dp, 40.dp).width(938.dp)) {
            JhHeader(if (showAll) "저장된 기록" else "항해 일지", goBack)
            Spacer(Modifier.height(24.dp))
            if (layers.isEmpty()) {
                Text("아직 남긴 기록이 없어요", style = JhType.BodyLargeRegular, color = JhColor.TextTertiary)
            } else if (showAll) {
                SavedRecords(layers, logs) { selected = it; showAll = false }
            } else {
                val layer = layers[selected.coerceIn(0, layers.lastIndex)]
                DayCard(layer, logs.filter { it.date == layer.date }.sortedBy { it.startedAt })
                Spacer(Modifier.height(28.dp))
                JhButton("일지 모아보기", { showAll = true }, Modifier.fillMaxWidth(), style = JhButtonStyle.Filled)
            }
        }
    }
}

/** 23: 그날 카드 — 선실 배너 · 날짜 · 휴식 기록 */
@Composable
private fun DayCard(layer: StrataLayer, day: List<DiveLog>) {
    val shape = RoundedCornerShape(32.dp)
    val time = remember { DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN) }
    fun hm(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(time)
    Column(
        Modifier.fillMaxWidth().height(832.dp).clip(shape).background(Color(0x66101418))
            .border(1.dp, JhColor.DividerTertiary, shape).padding(24.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(262.dp).clip(RoundedCornerShape(24.dp))) {
            Image(painterResource(layer.cabin.banner), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x00000000), Color(0x99080B10)))))
            if (layer.date == LocalDate.now()) {
                Text(
                    "오늘", style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.TextSecondary,
                    modifier = Modifier.offset(28.dp, 24.dp),
                )
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 24.dp)) {
                Text("${layer.date.monthValue}월 ${layer.date.dayOfMonth}일", style = JhType.HeadlineSmall)
                Text(layer.cabin.title, style = JhType.BodyMediumRegular, color = JhColor.TextSecondary)
            }
        }
        Spacer(Modifier.height(36.dp))
        Text("휴식 기록", style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.TextTertiary, modifier = Modifier.padding(start = 12.dp))
        Spacer(Modifier.height(16.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            day.forEach { log ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(JhColor.TextSecondary))
                        Spacer(Modifier.width(20.dp))
                        Text("${hm(log.startedAt)} – ${hm(log.endedAt).substringAfter(' ')}", style = JhType.BodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            "${log.restMin}분" + (log.endType.label?.let { " ($it)" } ?: ""),
                            style = JhType.BodyMedium,
                        )
                    }
                    if (log.memo.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("“${log.memo}”", style = JhType.BodyMediumRegular, color = JhColor.TextTertiary, modifier = Modifier.padding(start = 28.dp))
                    }
                }
            }
        }
    }
}

/** 23-M: 나의 항해 일지 — 최근 8일을 달별로 */
@Composable
private fun SavedRecords(layers: List<StrataLayer>, logs: List<DiveLog>, onPick: (Int) -> Unit) {
    val recent = layers.take(RECENT_DAYS)
    val truncated = layers.size > recent.size
    val groups = recent.withIndex().groupBy { java.time.YearMonth.from(it.value.date) }.entries.toList()
    Text("나의 항해 일지", style = JhType.TitleMedium)
    Spacer(Modifier.height(8.dp))
    Text("${layers.size}일의 기록 중 최근 ${recent.size}일", style = JhType.BodyMediumRegular, color = JhColor.TextTertiary)
    Spacer(Modifier.height(24.dp))
    val shape = RoundedCornerShape(32.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0x66101418))
            .border(1.dp, JhColor.DividerTertiary, shape).padding(horizontal = 36.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEachIndexed { gi, (month, items) ->
            if (gi > 0) Spacer(Modifier.height(24.dp))
            val days = items.map { it.value }
            val count = logs.count { java.time.YearMonth.from(it.date) == month && days.any { d -> d.date == it.date } }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${month.monthValue}월", style = JhType.BodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (truncated && groups.size > 1 && gi == groups.lastIndex) "최근 ${days.size}일"
                    else "${days.size}일 동안 ${count}번, ${days.sumOf { it.totalMin }}분",
                    style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.TextTertiary,
                )
            }
            items.forEach { (i, layer) ->
                Row(
                    Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).clickable { onPick(i) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${layer.date.monthValue}/${layer.date.dayOfMonth}", style = JhType.BodyMedium, modifier = Modifier.width(140.dp))
                    Text(
                        if (layer.count > 1) "${layer.cabin.title}에서 ${layer.count}번" else layer.cabin.title,
                        style = JhType.BodyMediumRegular, color = JhColor.TextSecondary, modifier = Modifier.weight(1f),
                    )
                    Text("${layer.totalMin}분", style = JhType.BodyMedium)
                    Icon(pleos.jamhang.ui.theme.PlIcons.ArrowRight, null, tint = JhColor.TextTertiary, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

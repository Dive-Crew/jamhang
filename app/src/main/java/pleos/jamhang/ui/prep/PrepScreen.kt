package pleos.jamhang.ui.prep

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pleos.jamhang.AppContainer
import pleos.jamhang.DivePlan
import pleos.jamhang.R
import pleos.jamhang.data.Cabin
import pleos.jamhang.data.DiveLogStore
import pleos.jamhang.data.UnlockConfig
import pleos.jamhang.session.DiveService
import pleos.jamhang.ui.components.AmbientBackground
import pleos.jamhang.ui.components.JhButton
import pleos.jamhang.ui.components.JhButtonStyle
import pleos.jamhang.ui.components.JhHeader
import pleos.jamhang.ui.components.JhPopup
import pleos.jamhang.ui.theme.JhBackground
import pleos.jamhang.ui.theme.JhColor
import pleos.jamhang.ui.theme.JhType

private val DURATIONS = listOf(10, 15, 20)

/** 02 잠항 준비: 시간(10·15·20) + 끝나는 시각 + 오늘의 선실 + 호흡 안내 + 잠깐 적어 두기 */
@Composable
fun PrepScreen(c: AppContainer, plan: DivePlan, onBack: () -> Unit, onStart: () -> Unit) {
    val logs by c.logs.logs.collectAsStateWithLifecycle()
    val restricted by c.restriction.restricted.collectAsStateWithLifecycle()
    val depth = remember(logs) { DiveLogStore.depthOf(logs) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    var memoOpen by remember { mutableStateOf(false) }
    if (!UnlockConfig.isUnlocked(plan.cabin, depth)) plan.cabin = Cabin.LIGHT_SEA

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(bg = JhBackground.Prep)
        JhHeader("잠항 준비", onBack, Modifier.offset(48.dp, 40.dp).width(1604.dp))
        Box(Modifier.offset(0.dp, 168.dp).fillMaxWidth().height(1.dp).background(JhColor.DividerSecondary))

        DepthTrack(plan.minutes, Modifier.offset(0.dp, 0.dp)) { plan.minutes = it }

        Column(Modifier.offset(840.dp, 200.dp).width(812.dp)) {
            Text("${plan.minutes}분 쉬면", style = JhType.BodyMediumRegular, color = JhColor.TextTertiary)
            Spacer(Modifier.height(12.dp))
            Text("${DiveService.formatClock(now + plan.minutes * 60_000L)}에 마쳐요", style = JhType.HeadlineMedium)
            // 세로 위치는 Figma 02 기준: 세그먼트 440, "어디서 쉴까요?" 564, 선실 카드 620, 호흡 안내 876
            Spacer(Modifier.height(120.dp))
            Segmented(plan.minutes) { plan.minutes = it }
            Spacer(Modifier.height(24.dp))
            Text("어디서 쉴까요?", style = JhType.BodyMedium)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Cabin.entries.forEach { cabin ->
                    CabinCard(
                        cabin, selected = cabin == plan.cabin,
                        unlocked = UnlockConfig.isUnlocked(cabin, depth),
                        Modifier.weight(1f),
                    ) { plan.cabin = cabin }
                }
            }
            Spacer(Modifier.height(44.dp))
            BreathChip(plan.breathGuide) { plan.breathGuide = !plan.breathGuide }
        }

        Row(
            Modifier.offset(840.dp, 1036.dp).width(812.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // 키보드 입력은 주차 중(규제 없음)에만
            JhButton(
                if (plan.memo.isBlank()) "잠깐 적어 두기" else "적어 둔 것 고치기",
                { memoOpen = true }, Modifier.width(300.dp), enabled = !restricted,
            )
            JhButton("${plan.minutes}분 쉬기", onStart, Modifier.weight(1f), style = JhButtonStyle.Filled)
        }

        if (memoOpen) MemoSheet(plan.memo, onClose = { memoOpen = false }) { plan.memo = it; memoOpen = false }
    }
}

/** 깊이 트랙 (표시 전용 · 손잡이 없음, PDF 피드백 5) — 점을 눌러 고를 수는 있다 */
@Composable
private fun DepthTrack(minutes: Int, modifier: Modifier, onPick: (Int) -> Unit) {
    val idx = DURATIONS.indexOf(minutes).coerceAtLeast(0)
    val stopY = listOf(308, 588, 868)
    val subY by animateDpAsState(stopY[idx].dp - 40.dp, tween(500), label = "sub")
    Box(modifier.size(800.dp, 1184.dp)) {
        Box(Modifier.offset(357.dp, 240.dp).size(6.dp, 720.dp).clip(CircleShape).background(Color(0x33FFFFFF)))
        Box(Modifier.offset(357.dp, 240.dp).size(6.dp, subY - 188.dp).clip(CircleShape).background(JhColor.Steel))
        DURATIONS.forEachIndexed { i, m ->
            val sel = i == idx
            Box(
                Modifier.offset(348.dp, stopY[i].dp).size(24.dp).clip(CircleShape)
                    .background(if (sel) JhColor.Steel else Color(0x66FFFFFF)),
            )
            Text(
                "${m}분", style = if (sel) JhType.BodyLarge else JhType.BodyLargeRegular,
                color = if (sel) JhColor.TextPrimary else JhColor.TextTertiary,
                modifier = Modifier.offset(220.dp, stopY[i].dp - 7.dp).clickable { onPick(m) },
            )
        }
        Box(
            Modifier.offset(408.dp, subY).size(184.dp, 104.dp).shadow(16.dp, RoundedCornerShape(52.dp))
                .clip(RoundedCornerShape(52.dp)).background(Color(0xFFF2F3F5)),
            contentAlignment = Alignment.Center,
        ) { Image(painterResource(R.drawable.ic_submarine), null, Modifier.size(92.dp, 56.dp)) }
    }
}

/** ivi_segmented_control/solid */
@Composable
private fun Segmented(value: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(28.dp)).background(JhColor.ButtonBasic).padding(8.dp),
    ) {
        DURATIONS.forEach { m ->
            val sel = m == value
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(22.dp))
                    .background(if (sel) Color(0x33FFFFFF) else Color.Transparent)
                    .clickable { onPick(m) },
                contentAlignment = Alignment.Center,
            ) { Text("${m}분", style = JhType.BodyLarge, color = if (sel) JhColor.TextPrimary else JhColor.TextSecondary) }
        }
    }
}

@Composable
private fun CabinCard(cabin: Cabin, selected: Boolean, unlocked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.height(212.dp).clip(shape)
            .background(if (selected) Color(0xFFF2F3F5) else JhColor.ButtonBasic)
            .border(1.dp, JhColor.DividerSecondary, shape)
            .clickable(enabled = unlocked, onClick = onClick)
            .padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(94.dp).clip(RoundedCornerShape(14.dp))) {
            Image(
                painterResource(cabin.thumb), null, Modifier.fillMaxSize().alpha(if (unlocked) 1f else 0.7f),
                contentScale = ContentScale.Crop,
            )
            if (!unlocked) Icon(
                Icons.Rounded.Lock, "잠김", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        val fg = if (selected) JhColor.TextOnFilled else JhColor.TextPrimary
        Text(cabin.title, style = JhType.BodyMedium, color = if (unlocked || selected) fg else JhColor.TextSecondary, modifier = Modifier.padding(start = 8.dp))
        Text(
            if (unlocked) cabin.subtitle else "쉬다 보면 열려요",
            style = JhType.Caption.copy(fontWeight = FontWeight.Normal),
            color = if (selected) Color(0x99131417) else JhColor.TextTertiary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * jamhang/breath-toggle (Figma 11/12: 칩 → 가로 전체 812×120 카드, 꺼짐/켜짐 두 상태).
 * 카드 안 문구는 Figma 레이어 이름으로 확인할 수 없어 기존 문구 유지 — 화면 확인 후 맞출 것.
 */
@Composable
private fun BreathChip(on: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().height(120.dp).clip(shape)
            .background(if (on) Color(0x26FFFFFF) else JhColor.ButtonBasic)
            .border(1.dp, if (on) JhColor.DividerTertiary else JhColor.DividerSecondary, shape)
            .clickable(onClick = onToggle).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Figma B00: ✓ 호흡 안내로 시작 ··· 켜짐
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (on) androidx.compose.material3.Icon(pleos.jamhang.ui.theme.PlIcons.Check, null, tint = JhColor.Mint, modifier = Modifier.size(32.dp))
            else Box(Modifier.size(24.dp).border(2.dp, JhColor.TextTertiary, CircleShape))
        }
        Spacer(Modifier.width(20.dp))
        Text("호흡 안내로 시작", style = JhType.BodyLarge, color = if (on) JhColor.TextPrimary else JhColor.TextSecondary, modifier = Modifier.weight(1f))
        Text(if (on) "켜짐" else "꺼짐", style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = if (on) JhColor.TextSecondary else JhColor.TextTertiary)
    }
}

/** 떠오르는 일 남기기 (키보드 · 주차 중에만) */
@Composable
private fun MemoSheet(initial: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    JhPopup(onDismiss = onClose, width = 1200.dp) {
        Column {
            Text("계속 마음에 걸리는 일이 있나요?", style = JhType.TitleMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                "잠깐 적어 두고 쉬어요. 쉬고 나면 다시 보여 드릴게요. 저장 없이 쉬면 화면을 닫을 때 메모도 사라져요.",
                style = JhType.BodyMediumRegular, color = JhColor.TextSecondary,
            )
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(80) },
                placeholder = { Text("예: 집에 가는 길에 우유 사기", style = JhType.BodyLargeRegular, color = JhColor.TextQuaternary) },
                textStyle = JhType.BodyLargeRegular, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JhColor.Steel, unfocusedBorderColor = JhColor.DividerTertiary,
                    cursorColor = JhColor.TextPrimary,
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(100.dp),
            )
            Spacer(Modifier.height(40.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                JhButton("남기지 않기", { onSave("") }, Modifier.weight(1f))
                JhButton("남기기", { onSave(text.trim()) }, Modifier.weight(1f), style = JhButtonStyle.Filled)
            }
        }
    }
}

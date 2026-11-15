package pleos.jamhang.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pleos.jamhang.AppContainer
import pleos.jamhang.voice.VoiceInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import pleos.jamhang.data.DiveLogStore
import pleos.jamhang.data.EndType
import pleos.jamhang.session.DiveResult
import pleos.jamhang.session.SessionState
import pleos.jamhang.ui.components.AmbientBackground
import pleos.jamhang.ui.components.JhBadge
import pleos.jamhang.ui.components.JhButton
import pleos.jamhang.ui.components.JhButtonSize
import pleos.jamhang.ui.components.JhButtonStyle
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import pleos.jamhang.ui.components.JhToast
import pleos.jamhang.ui.components.Stat
import pleos.jamhang.ui.components.StatRow
import pleos.jamhang.ui.components.StrataPanel
import pleos.jamhang.ui.components.formatDepth
import pleos.jamhang.ui.theme.JhBackground
import pleos.jamhang.ui.theme.JhColor
import pleos.jamhang.ui.theme.JhType

/** 07 부상 완료 · 07-2 두 번째 · 07-3 저장 없이 · 16-2 멈춘 사이 종료 · 08 자동 부상 */
@Composable
fun ResultScreen(c: AppContainer, onHome: () -> Unit, onStrata: () -> Unit) {
    val state by c.session.state.collectAsStateWithLifecycle()
    val result = remember { (state as? SessionState.Ended)?.result } ?: run { LaunchedEffect(Unit) { onHome() }; return }
    val logs by c.logs.logs.collectAsStateWithLifecycle()
    val restricted by c.restriction.restricted.collectAsStateWithLifecycle()
    val memoOn by c.profile.memoOn.collectAsStateWithLifecycle()
    val layers = remember(logs) { DiveLogStore.layers(logs) }
    val copy = remember(result) { ResultCopy.of(result) }
    var toast by remember { mutableStateOf(if (result.log.endType == EndType.AUTO && result.saved) "휴식을 마치고 기록을 저장했어요" else null) }
    LaunchedEffect(toast) { if (toast != null) { delay(3000); toast = null } }
    // 07 결과 진입음, 그날 첫 적립이면 지층 +1겹 소리를 겹 모션(0.3초 뒤 시작)에 맞춰
    LaunchedEffect(result.log.id) {
        c.sound.play(pleos.jamhang.session.SoundEngine.Sfx.SURFACE_COMPLETE)
        if (result.log.credited) c.sound.playLater(pleos.jamhang.session.SoundEngine.Sfx.LAYER_ADDED, 300)
    }

    val focus = LocalFocusManager.current
    // 배경을 누르면 키보드만 닫는다 (입력한 한마디는 저장하지 않음)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }) {
        AmbientBackground(bg = JhBackground.Result)
        StrataPanel(
            layers, result.totalDepth, Modifier.offset(48.dp, 48.dp),
            newLayerToday = result.log.credited,
            onClick = onStrata,
        )
        Column(Modifier.offset(712.dp, 96.dp).width(940.dp)) {
            JhBadge(copy.badge, if (result.log.endType == EndType.AUTO) JhColor.Amber else JhColor.TextSecondary)
            Spacer(Modifier.height(24.dp))
            Text(copy.title, style = JhType.HeadlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(copy.body, style = JhType.BodyLargeRegular, color = JhColor.TextSecondary)
            Spacer(Modifier.height(56.dp))
            if (result.saved) {
                StatRow(
                    listOf(
                        Stat("휴식 시간", "${result.log.restMin}분"),
                        // 07-2: 수심이 늘어난 것처럼 보이지 않게
                        if (result.log.credited) Stat("오늘의 겹", "+1겹") else Stat("오늘 휴식", "${c.session.todayCount()}번"),
                        Stat("누적 수심", formatDepth(result.totalDepth)),
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text("전체 ${result.log.plannedMin}분", style = JhType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = JhColor.TextTertiary)
                Spacer(Modifier.height(40.dp))
                // 12 "한 줄 메모 저장"을 끄면 입력칸을 보이지 않는다
                if (result.log.endType != EndType.AUTO && memoOn) MemoField(c.voice, enabled = !restricted, initial = result.log.memo) {
                    c.logs.updateMemo(result.log.id, it)
                }
            }
        }
        if (result.log.endType == EndType.AUTO || !result.saved) {
            JhButton("확인", onHome, Modifier.offset(712.dp, 884.dp).width(940.dp), style = JhButtonStyle.Filled)
        } else {
            Row(Modifier.offset(712.dp, 1036.dp).width(940.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                JhButton("지층 보기", onStrata, Modifier.weight(1f))
                JhButton("홈으로", onHome, Modifier.weight(1f), style = JhButtonStyle.Filled)
            }
        }
        JhToast(toast)
    }
}

/**
 * 오늘의 한마디 (키보드 · STT 대체, 주차 중에만).
 * 입력 중인 글은 초안일 뿐이고, 키보드 [확인] 또는 [남기기]를 눌러야 항해 일지에 저장된다.
 * 확인 없이 배경을 누르거나 화면을 떠나면 저장하지 않는다.
 */
@Composable
private fun MemoField(voice: VoiceInput, enabled: Boolean, initial: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var saved by remember { mutableStateOf(initial) }
    val focus = LocalFocusManager.current
    // 음성 입력: 말한 내용은 글자로만 받아 입력칸에 채운다 (저장은 [남기기]를 눌러야)
    val voiceOn by voice.available.collectAsStateWithLifecycle()
    val voiceState by voice.state.collectAsStateWithLifecycle()
    val listening = voiceState as? VoiceInput.State.Listening
    var voiceError by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { voice.cancel() } }
    LaunchedEffect(enabled) { if (!enabled) voice.cancel() }
    val dirty = text.trim() != saved
    val commit = {
        if (dirty) { saved = text.trim(); text = saved; onSave(saved) }
        focus.clearFocus()
    }
    Column {
    OutlinedTextField(
        value = listening?.partial?.takeIf { it.isNotEmpty() } ?: text, onValueChange = { text = it.take(60) },
        enabled = enabled && listening == null,
        placeholder = {
            Text(
                when {
                    !enabled -> "주차 중에 남길 수 있어요"
                    listening != null -> "듣고 있어요. 말을 마치면 글자로 바꿔 드려요"
                    else -> "오늘의 한마디 남기기"
                },
                style = JhType.BodyLargeRegular, color = JhColor.TextTertiary,
            )
        },
        leadingIcon = if (enabled && voiceOn) ({
            val tint = if (listening != null) JhColor.Mint else JhColor.TextSecondary
            androidx.compose.foundation.layout.Box(
                Modifier.padding(start = 12.dp).size(64.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (listening != null) JhColor.Mint.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable {
                        voiceError = null
                        if (listening != null) voice.stop()
                        else voice.start(onText = { text = it.take(60) }, onFail = { voiceError = it })
                    },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    if (listening != null) pleos.jamhang.ui.theme.PlIcons.Mic else pleos.jamhang.ui.theme.PlIcons.Mic,
                    if (listening != null) "음성 입력 멈추기" else "음성으로 남기기",
                    tint = tint, modifier = Modifier.size(34.dp),
                )
            }
        }) else null,
        trailingIcon = if (dirty) ({
            JhButton("남기기", commit, Modifier.padding(end = 10.dp), style = JhButtonStyle.Filled, size = JhButtonSize.Medium)
        }) else null,
        textStyle = JhType.BodyLargeRegular, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = JhColor.Steel, unfocusedBorderColor = JhColor.DividerSecondary,
            disabledBorderColor = JhColor.DividerSecondary, cursorColor = JhColor.TextPrimary,
            unfocusedContainerColor = JhColor.ButtonBasic, focusedContainerColor = JhColor.ButtonBasic,
            disabledContainerColor = JhColor.ButtonBasic,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(100.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text(
        when {
            voiceError != null -> voiceError!!
            listening != null -> "음성은 저장하지 않고 글자로 바꾼 내용만 남겨요."
            dirty -> "아직 저장하지 않았어요. [남기기]를 눌러야 일지에 남아요."
            saved.isNotEmpty() -> "항해 일지에 남겼어요"
            else -> ""
        },
        style = JhType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
        color = if (dirty || voiceError != null) JhColor.Amber else JhColor.TextTertiary,
    )
    }
}

private data class ResultCopy(val badge: String, val title: String, val body: String) {
    companion object {
        fun of(r: DiveResult): ResultCopy {
            val m = r.log.restMin
            return when {
                r.log.endType == EndType.AUTO -> ResultCopy(
                    "자동 부상", "${m}분 쉬었어요", "기어가 바뀌어 마쳤어요. 쉰 시간은 ${m}분이에요.",
                )
                !r.saved -> ResultCopy(
                    "부상 완료", "잘 쉬었어요",
                    if (m < 1) "1분보다 짧은 휴식은 기록하지 않아요." else "이번 휴식은 기록하지 않았어요.",
                )
                r.log.endType == EndType.PAUSED_END -> ResultCopy(
                    "부상 완료", "${m}분 쉬었어요", "멈춘 사이에 끝나는 시각이 지나 마쳤어요.",
                )
                r.secondToday -> ResultCopy("오늘의 추가 휴식", "이번 휴식을 마쳤어요", "오늘 두 번째 휴식이 기록됐어요.")
                r.log.endType == EndType.EARLY -> ResultCopy(
                    "부상 완료", "${m}분 쉬었어요",
                    if (r.log.credited) "조금 일찍 마쳤지만 오늘 지층이 한 겹 쌓였어요." else "조금 일찍 마쳤어요. 휴식은 기록했어요.",
                )
                r.log.credited -> ResultCopy("부상 완료", "잠항을 마쳤어요", "오늘 지층이 한 겹 쌓였어요.")
                else -> ResultCopy("부상 완료", "잠항을 마쳤어요", "휴식을 기록했어요.")
            }
        }
    }
}

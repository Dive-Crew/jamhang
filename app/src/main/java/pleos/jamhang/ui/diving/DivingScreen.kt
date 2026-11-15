package pleos.jamhang.ui.diving

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pleos.jamhang.AppContainer
import pleos.jamhang.R
import pleos.jamhang.data.EndType
import pleos.jamhang.session.ActiveSession
import pleos.jamhang.session.BreathScript
import pleos.jamhang.session.DiveService.Companion.formatClock
import pleos.jamhang.session.SessionState
import pleos.jamhang.ui.components.JhButton
import pleos.jamhang.ui.components.JhButtonStyle
import pleos.jamhang.ui.components.JhPopup
import pleos.jamhang.ui.components.PopupTwoButtons
import pleos.jamhang.ui.theme.JhColor
import pleos.jamhang.ui.theme.OneMinuteBackground
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import pleos.jamhang.ui.theme.JhType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 04 머무름 (+ 06 일찍 마침, 15/16 잠시 멈춤, 19 1분 전, 20 5분 연장) */
@Composable
fun DivingScreen(c: AppContainer, onTestMenu: () -> Unit) {
    val state by c.session.state.collectAsStateWithLifecycle()
    val s = (state as? SessionState.Active)?.session ?: return
    val restricted by c.restriction.restricted.collectAsStateWithLifecycle()
    val vehicle by c.vehicle.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }

    var confirmEnd by remember { mutableStateOf(false) }
    var extendOpen by remember { mutableStateOf(false) }
    val warning = s.isWarning(now)

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.bg_reef), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        // 호흡 안내 중(B01–B05)에는 히어로 대신 호흡 원 화면
        if (s.breathGuide) {
            BreathGuide(
                s, animate = !restricted,
                onSkip = { c.session.setBreathGuide(false) },
                onPause = { c.session.pause(pleos.jamhang.session.PauseReason.USER) },
                // B05: 이어서 쉬면 호흡 안내를 반복하지 않고 조용한 휴식으로
                onResume = { c.session.setBreathGuide(false); c.session.resume() },
                onFinish = { c.session.finish(EndType.EARLY) },
            )
        } else {
            Hero(s, animate = !restricted)
        }
        EndTimeChip(s.endAt, Modifier.offset(48.dp, 32.dp), onLongPress = onTestMenu)
        Gauge(s, now, Modifier.offset(1280.dp, 0.dp))

        // B05 중단 분기: 도크 숨김, 직접 이어서 쉬기
        if (!(s.breathGuide && s.isPaused)) Dock(
            s, Modifier.offset(48.dp, 1024.dp),
            onToggleSound = { c.session.setSoundOn(!s.soundOn) },
            onVolume = { c.session.setVolume(it) },
            onEnd = { confirmEnd = true },
        )

        // 19 종료 1분 전 예고 (마지막 1분만 숫자)
        AnimatedVisibility(warning && !extendOpen, enter = fadeIn(tween(800)), exit = fadeOut(tween(400))) {
            OneMinute(
                remainingMs = s.remainingMs(now),
                onExtend = { extendOpen = true },
                onFinish = { c.session.finish(EndType.COMPLETED) },
            )
        }

        // 정책(Figma "문 열림: 중앙 팝업"): 문을 열면 바로 끝내지 않고 멈춘 뒤 가운데 팝업으로 묻는다.
        // 열려 있는 동안은 [마치기]만, 닫히면 [이어서 쉬기]도 고를 수 있다. 끝나는 시각은 그대로 흐른다.
        if (s.pauseReason == pleos.jamhang.session.PauseReason.DOOR) {
            JhPopup(onDismiss = null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (s.awaitingResume) "문이 닫혔어요" else "문이 열려 잠시 멈췄어요", style = JhType.TitleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (s.awaitingResume) "이어서 쉴까요? 끝나는 시각은 ${formatClock(s.endAt)} 그대로예요."
                        else "내리시려면 [마치기]를 눌러 주세요.\n문을 닫으면 이어서 쉴 수 있어요.",
                        style = JhType.BodyMediumRegular, color = JhColor.TextSecondary, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(40.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        JhButton("마치기", { c.session.finish(EndType.EARLY) }, Modifier.weight(1f))
                        JhButton(
                            "이어서 쉬기", { c.session.resume() }, Modifier.weight(1f),
                            style = JhButtonStyle.Filled, enabled = s.awaitingResume,
                        )
                    }
                }
            }
        }

        if (confirmEnd && !warning) {
            JhPopup(onDismiss = { confirmEnd = false }) {
                PopupTwoButtons(
                    "지금 마칠까요?",
                    primary = "마치기", onPrimary = { confirmEnd = false; c.session.finish(EndType.EARLY) },
                    secondary = "계속 쉬기", onSecondary = { confirmEnd = false },
                )
            }
        }

        if (extendOpen) {
            val parkedOk = !vehicle.gearReadable || vehicle.isParked
            val newEnd = s.endAt + ActiveSession.EXTEND_MS
            // Figma 20: 바다 배경을 어둡게 깐 게 아니라 거의 검정 단색 위에 팝업
            // 크기·간격은 Figma 20 렌더 기준: 1000 × ~800, 좌우 56, 위 56, 아래 98
            JhPopup(
                onDismiss = null, width = 1000.dp, scrim = JhColor.BgSolidDark,
                padding = PaddingValues(start = 56.dp, end = 56.dp, top = 56.dp, bottom = 98.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("5분 더 쉴까요?", style = JhType.TitleMedium)
                    Spacer(Modifier.height(34.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatClock(s.endAt), style = JhType.TitleMedium.copy(fontWeight = FontWeight.Normal), color = JhColor.TextTertiary)
                        Text("   →   ", style = JhType.TitleMedium.copy(fontWeight = FontWeight.Normal), color = JhColor.TextTertiary)
                        Text(formatClock(newEnd), style = JhType.HeadlineSmall, color = JhColor.Steel)
                    }
                    Spacer(Modifier.height(89.dp))
                    Column(
                        Modifier.fillMaxWidth().height(247.dp).clip(RoundedCornerShape(28.dp)).background(JhColor.PopupInset)
                            .padding(horizontal = 32.dp, vertical = 24.dp),
                    ) {
                        Text("주차 상태를 확인했어요", style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.TextTertiary)
                        Spacer(Modifier.height(24.dp))
                        RecheckRow("기어 P단", parkedOk)
                        Spacer(Modifier.height(32.dp))
                        RecheckRow("화면 제한 없음", !restricted)
                    }
                    Spacer(Modifier.height(62.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        JhButton("그대로 마치기", { extendOpen = false; c.session.finish(EndType.COMPLETED) }, Modifier.weight(1f))
                        JhButton(
                            "${formatClock(newEnd).substringAfter(' ')}까지 쉬기",
                            { extendOpen = false; c.session.extend() },
                            Modifier.weight(1f), style = JhButtonStyle.Filled, enabled = parkedOk && !restricted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(s: ActiveSession, animate: Boolean) {
    val t = rememberInfiniteTransition(label = "bob")
    val bob by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    Box(Modifier.offset(140.dp, 300.dp).size(1000.dp, 460.dp)) {
        val label = when {
            s.awaitingResume -> "이어서 쉴까요?"
            s.isPaused -> "잠시 멈췄어요"
            else -> "조용히 쉬는 중"
        }
        Text(
            label, style = JhType.TitleSmall.copy(fontWeight = FontWeight.Medium), color = JhColor.TextSecondary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        )
        Image(
            painterResource(R.drawable.ic_submarine), "잠수함",
            Modifier.offset(250.dp, 150.dp).size(490.dp, 200.dp)
                .graphicsLayer { translationY = if (animate && !s.isPaused) bob * density else 0f },
        )
        if (s.isPaused) PausedBar(s, Modifier.align(Alignment.BottomCenter).padding(bottom = 0.dp))
    }
}

/** 15 잠시 멈춤 / 16 이어서 쉬기 */
@Composable
private fun PausedBar(s: ActiveSession, modifier: Modifier) {
    val c = (androidx.compose.ui.platform.LocalContext.current.applicationContext as pleos.jamhang.JamhangApp).container
    if (s.awaitingResume) {
        JhButton("이어서 쉬기", { c.session.resume() }, modifier.width(420.dp), style = JhButtonStyle.Filled)
    } else {
        Row(
            modifier.width(425.dp).height(62.dp).clip(CircleShape).background(Color(0x66324250))
                .border(1.dp, JhColor.DividerTertiary, CircleShape).padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(pleos.jamhang.ui.theme.PlIcons.SoundMute, "소리 멈춤", tint = JhColor.TextSecondary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Text("끝나는 시각은 그대로예요", style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.TextSecondary)
        }
    }
}

@Composable
private fun EndTimeChip(endAt: Long, modifier: Modifier, onLongPress: () -> Unit) {
    Box(
        modifier.height(60.dp).clip(CircleShape).background(Color(0x40182430))
            .border(1.dp, JhColor.DividerTertiary, CircleShape)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text("${formatClock(endAt)}에 마쳐요", style = JhType.BodyMedium.copy(fontWeight = FontWeight.SemiBold)) }
}

/** 진행 게이지 (숫자 없음): 시작 ~ 마침, 현재 위치 점 */
@Composable
private fun Gauge(s: ActiveSession, now: Long, modifier: Modifier) {
    val fmt = remember { DateTimeFormatter.ofPattern("h:mm") }
    fun hm(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fmt)
    val p = ((now - s.startedAt).toFloat() / (s.endAt - s.startedAt)).coerceIn(0f, 1f)
    Box(modifier.size(420.dp, 1184.dp)) {
        Text("${hm(s.startedAt)} 시작", style = JhType.Caption.copy(fontSize = JhType.Caption.fontSize, fontWeight = FontWeight.Normal), color = JhColor.TextTertiary, modifier = Modifier.offset(190.dp, 120.dp))
        Text("${hm(s.endAt)} 마침", style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.TextTertiary, modifier = Modifier.offset(190.dp, 1000.dp))
        Canvas(Modifier.offset(290.dp, 140.dp).size(40.dp, 880.dp)) {
            val x = size.width / 2
            drawLine(Color(0x52FFFFFF), Offset(x, 0f), Offset(x, size.height), 4.dp.toPx())
            drawLine(Color(0x52FFFFFF), Offset(x - 10.dp.toPx(), 0f), Offset(x + 10.dp.toPx(), 0f), 2.dp.toPx())
            drawLine(Color(0x52FFFFFF), Offset(x - 10.dp.toPx(), size.height), Offset(x + 10.dp.toPx(), size.height), 2.dp.toPx())
            drawLine(Color(0xCCDDE3EC), Offset(x, 0f), Offset(x, size.height * p), 4.dp.toPx())
            drawCircle(Color(0xFFDDE3EC), 12.dp.toPx(), Offset(x, size.height * p))
        }
    }
}

/**
 * 호흡 안내 약 1분 (Figma B01–B05). 박자는 [BreathScript] — 소리(DiveService)와 같은 기준 시각.
 * B01 편한 호흡으로 시작 → B02 들숨(원 확장) ↔ B03 날숨(원 축소) ×5 → B04 완료 → (서비스가 안내를 끔)
 * B05 잠시 멈춤: 자동 재개 없이 [이어서 쉬기] / [지금 마치기].
 * 주행 규제 중에는 원 크기 변화 없이 문구만 바뀐다.
 */
@Composable
private fun BreathGuide(
    s: ActiveSession, animate: Boolean,
    onSkip: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onFinish: () -> Unit,
) {
    var t by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { androidx.compose.runtime.withFrameMillis { t = System.currentTimeMillis() } } }
    val elapsed = (t - s.breathStartedAt).coerceAtLeast(0)
    val phase = if (s.isPaused) null else BreathScript.phaseAt(elapsed)
    val p = BreathScript.progressAt(elapsed)
    // 들숨이 끝날 때 원이 가장 크다. 시작·완료·멈춤은 기본 크기
    val eased = (1 - kotlin.math.cos(Math.PI * p).toFloat()) / 2
    val grow = when {
        !animate -> 0f
        phase == BreathScript.Phase.INHALE -> eased
        phase == BreathScript.Phase.EXHALE -> 1 - eased
        else -> 0f
    }
    val (label, title, body) = when (phase) {
        null -> Triple("호흡 안내 일시 중단", "잠시 멈췄어요", "준비되면 이어서 쉴 수 있어요.\n끝나는 시각은 그대로예요.")
        BreathScript.Phase.INTRO -> Triple("호흡 안내 · 약 1분", "편한 호흡으로 시작해요", "원이 천천히 커졌다 작아져요.\n맞추기 어렵다면 평소처럼 쉬어도 좋아요.")
        BreathScript.Phase.INHALE -> Triple("호흡 안내 · 약 1분", "천천히 들이쉬어요", "원이 커지는 동안, 편안한 만큼만")
        BreathScript.Phase.EXHALE -> Triple("호흡 안내 · 약 1분", "천천히 내쉬어요", "원이 작아지는 동안, 힘을 풀어 주세요")
        else -> Triple("호흡 안내 완료", "이제 편하게 쉬어요", "안내가 끝났어요.\n평소 호흡으로 편하게 쉬세요.")
    }

    // 중앙 콘텐츠 (320,148) 1060×646
    Box(Modifier.offset(320.dp, 148.dp).size(1060.dp, 646.dp)) {
        Text(
            label, style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.TextSecondary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        // 호흡 원: 420 빛 + 280 원(들숨에 400까지)
        Box(Modifier.offset(320.dp, 50.dp).size(420.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r0 = 140.dp.toPx()
                val r = r0 * (1f + 0.43f * grow)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0x00A8C8E0), Color(0x33A8C8E0), Color(0x00A8C8E0)),
                        center = center, radius = size.minDimension / 2,
                    ),
                )
                drawCircle(Color(0x1FDDE8F4), r)
                drawCircle(Color(0x8CE6EEF6), r, style = Stroke(2.dp.toPx()))
            }
            Image(painterResource(R.drawable.ic_submarine), "잠수함", Modifier.size(284.dp, 120.dp))
        }
        Text(
            title, style = JhType.TitleMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().offset(y = 483.dp),
        )
        Text(
            body, style = JhType.BodyMediumRegular, color = JhColor.TextSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().offset(y = 568.dp),
        )
    }

    // 조작 (y 910): B01–B03 두 버튼 · B04 하나 · B05 이어서/마치기
    Row(
        Modifier.offset(0.dp, 910.dp).width(1700.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
    ) {
        when (phase) {
            null -> {
                val canResume = s.awaitingResume || s.pauseReason == pleos.jamhang.session.PauseReason.USER
                JhButton("이어서 쉬기", onResume, Modifier.width(420.dp), style = JhButtonStyle.Filled, enabled = canResume)
                JhButton("지금 마치기", onFinish, Modifier.width(300.dp))
            }
            BreathScript.Phase.DONE, BreathScript.Phase.OVER -> JhButton("조용히 쉬기", onSkip, Modifier.width(420.dp))
            else -> {
                JhButton("안내 없이 쉬기", onSkip, Modifier.width(420.dp))
                JhButton("잠시 멈추기", onPause, Modifier.width(300.dp))
            }
        }
    }
}

/** jamhang/dive-dock: 소리 토글 · ◀ 볼륨 ▶ · 마치기 */
@Composable
private fun Dock(
    s: ActiveSession, modifier: Modifier,
    onToggleSound: () -> Unit, onVolume: (Float) -> Unit, onEnd: () -> Unit,
) {
    Row(modifier.width(1604.dp).height(112.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.size(100.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x40182430))
                .border(1.dp, JhColor.DividerTertiary, RoundedCornerShape(24.dp)).clickable(onClick = onToggleSound),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (s.soundOn) pleos.jamhang.ui.theme.PlIcons.Sound else pleos.jamhang.ui.theme.PlIcons.SoundMute,
                if (s.soundOn) "소리 끄기" else "소리 켜기", tint = JhColor.TextPrimary, modifier = Modifier.size(36.dp),
            )
            Text(if (s.soundOn) "소리" else "꺼짐", style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.TextSecondary)
        }
        Spacer(Modifier.width(24.dp))
        Row(
            Modifier.width(800.dp).height(100.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x26182430)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(100.dp).clickable { onVolume(s.volume - 0.1f) }, contentAlignment = Alignment.Center) {
                Icon(pleos.jamhang.ui.theme.PlIcons.ArrowLeft, "소리 줄이기", tint = JhColor.TextPrimary, modifier = Modifier.size(56.dp))
            }
            VolumeSlider(if (s.soundOn) s.volume else 0f, onVolume, Modifier.weight(1f))
            Box(Modifier.size(100.dp).clickable { onVolume(s.volume + 0.1f) }, contentAlignment = Alignment.Center) {
                Icon(pleos.jamhang.ui.theme.PlIcons.ArrowRight, "소리 키우기", tint = JhColor.TextPrimary, modifier = Modifier.size(56.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.width(280.dp).height(100.dp).clip(RoundedCornerShape(32.dp)).background(Color(0x40182430))
                .border(1.dp, JhColor.DividerSecondary, RoundedCornerShape(32.dp)).clickable(onClick = onEnd),
            contentAlignment = Alignment.Center,
        ) { Text("마치기", style = JhType.BodyLarge) }
    }
}

/** ivi_slider_continuous: 채워진 막대 + 스피커 아이콘 */
@Composable
private fun VolumeSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    var widthPx by remember { mutableStateOf(1f) }
    Box(
        modifier.height(76.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x33FFFFFF))
            .pointerInput(Unit) {
                widthPx = size.width.toFloat()
                detectTapGestures { onChange(it.x / size.width) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { ch, _ -> onChange(ch.position.x / size.width) }
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0.08f, 1f)).background(Color(0xCCE6E8EE)))
        Icon(
            pleos.jamhang.ui.theme.PlIcons.Sound, "볼륨", tint = Color(0xFF55606C),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp).size(36.dp),
        )
    }
}

/** 19 종료 1분 전 예고 */
@Composable
private fun OneMinute(remainingMs: Long, onExtend: () -> Unit, onFinish: () -> Unit) {
    val sec = ((remainingMs + 999) / 1000).toInt()
    val frac = remainingMs / 60_000f
    Box(
        Modifier.fillMaxSize()
            .drawBehind {
                // 타원형 그라데이션: 원형 그라데이션을 세로로 눌러서 그린다
                val o = OneMinuteBackground
                drawRect(o.stops.last().second)
                scale(1f, o.RADIUS_Y / o.RADIUS_X, pivot = Offset(o.CENTER_X.dp.toPx(), o.CENTER_Y.dp.toPx())) {
                    drawCircle(
                        Brush.radialGradient(
                            *o.stops, center = Offset(o.CENTER_X.dp.toPx(), o.CENTER_Y.dp.toPx()), radius = o.RADIUS_X.dp.toPx(),
                        ),
                        radius = o.RADIUS_X.dp.toPx(), center = Offset(o.CENTER_X.dp.toPx(), o.CENTER_Y.dp.toPx()),
                    )
                }
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, o.BottomFade),
                        startY = o.BOTTOM_FADE_FROM.dp.toPx(), endY = size.height,
                    ),
                )
            }
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {},
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 190.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 10.dp.toPx()
                    drawCircle(Color(0x33FFFFFF), size.minDimension / 2 - w / 2, style = Stroke(w))
                    drawArc(
                        JhColor.Steel, -90f, 360f * frac, false, style = Stroke(w, cap = StrokeCap.Round),
                        topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    )
                }
                Text("%d:%02d".format(sec / 60, sec % 60), style = JhType.Countdown.copy(fontSize = JhType.Countdown.fontSize * 0.75f))
            }
            Spacer(Modifier.height(96.dp))
            Text("1분 뒤에 마쳐요", style = JhType.TitleMedium)
            Spacer(Modifier.height(16.dp))
            Text("이제 천천히 돌아올 준비를 해요", style = JhType.BodyMediumRegular, color = JhColor.TextTertiary)
            Spacer(Modifier.height(96.dp))
            Row(Modifier.width(800.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                JhButton("5분 더 쉬기", onExtend, Modifier.weight(1f))
                JhButton("마치기", onFinish, Modifier.weight(1f), style = JhButtonStyle.Filled)
            }
        }
    }
}

@Composable
private fun RecheckRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (ok) Color(0x33607890) else Color(0x33F2B880)),
            contentAlignment = Alignment.Center,
        ) { if (ok) Icon(pleos.jamhang.ui.theme.PlIcons.Check, null, tint = JhColor.Steel, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(24.dp))
        Text(label, style = JhType.BodyLargeRegular, modifier = Modifier.weight(1f))
        Text(if (ok) "확인됨" else "확인 안 됨", style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = if (ok) JhColor.TextTertiary else JhColor.Amber)
    }
}

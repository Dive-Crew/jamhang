package pleos.jamhang.ui.hatch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pleos.jamhang.AppContainer
import pleos.jamhang.R
import pleos.jamhang.session.SoundEngine
import pleos.jamhang.ui.components.AmbientBackground
import pleos.jamhang.ui.components.JhButton
import pleos.jamhang.ui.components.JhButtonSize
import pleos.jamhang.ui.components.JhButtonStyle
import pleos.jamhang.ui.components.JhHeader
import pleos.jamhang.ui.theme.JhBackground
import pleos.jamhang.ui.theme.JhColor
import pleos.jamhang.ui.theme.JhType

private const val HATCH_MS = 3_000

/** 03 해치 잠그기 (출발 의식): 해치 휠 연출 + 기어 P단(필수) · 문 잠김(지원 차량) */
@Composable
fun HatchScreen(c: AppContainer, onBack: () -> Unit, onDive: () -> Unit) {
    val v by c.vehicle.state.collectAsStateWithLifecycle()
    // 기어를 못 읽는 차량은 사용자 확인으로 대체 (§2 대안)
    var manualPark by remember { mutableStateOf(false) }
    val parked = if (v.gearReadable) v.isParked else manualPark
    val spin = remember { Animatable(0f) }
    var started by remember { mutableStateOf(false) }
    // 해치 닫히는 소리: 휠이 돌기 시작할 때 1회, "바로 시작"이나 P 이탈 시 즉시 정지
    var hatchStream by remember { mutableStateOf(0) }

    LaunchedEffect(parked) {
        if (!parked) { c.sound.stop(hatchStream); hatchStream = 0; return@LaunchedEffect }
        if (hatchStream == 0 && spin.value == 0f) hatchStream = c.sound.play(SoundEngine.Sfx.HATCH_CLOSE, respectMute = false)
        spin.animateTo(1f, tween(HATCH_MS - (spin.value * HATCH_MS).toInt(), easing = FastOutSlowInEasing))
        if (!started) { started = true; onDive() }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(bg = JhBackground.Hatch)
        JhHeader("쉬기 전 준비", onBack, Modifier.offset(48.dp, 40.dp).width(988.dp))

        Column(Modifier.offset(170.dp, 202.dp).width(620.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.art_hatch_wheel), "해치",
                Modifier.size(584.dp).rotate(spin.value * 180f),
            )
            Spacer(Modifier.height(48.dp))
            Text(
                if (parked) "해치를 닫고 있어요" else "P단이 되면 해치를 닫아요",
                style = JhType.BodyLargeRegular, color = JhColor.TextSecondary,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { i ->
                    val on = spin.value >= (i + 1) / 4f - 0.01f
                    Box(Modifier.size(32.dp, 3.dp).clip(CircleShape).background(if (on) JhColor.Steel else Color(0x33FFFFFF)))
                }
            }
        }

        Column(Modifier.offset(900.dp, 172.dp).width(752.dp)) {
            Text(if (parked) "쉬기 전 확인했어요" else "P단으로 바꿔 주세요", style = JhType.HeadlineSmall)
            Spacer(Modifier.height(48.dp))
            CheckRow(
                Icons.Rounded.LocalParking, "기어 P단", "필수",
                status = when {
                    parked -> "확인됨"
                    v.gearReadable -> "P단이 아니에요"
                    else -> "직접 확인해 주세요"
                },
                ok = parked,
                action = if (!v.gearReadable && !manualPark) ({
                    JhButton("P단이에요", { manualPark = true }, size = JhButtonSize.Medium)
                }) else null,
            )
            v.doorsLocked?.let { locked ->
                Spacer(Modifier.height(16.dp))
                CheckRow(
                    if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, "문 잠김", "지원 차량",
                    status = if (locked) "확인됨" else "열려 있어요", ok = locked,
                )
            }
        }

        // "건너뛰기" → "바로 시작" (PDF 피드백 6): 연출만 건너뜀, P단은 그대로 필요
        JhButton(
            "바로 시작", { if (!started) { started = true; c.sound.stop(hatchStream); onDive() } },
            Modifier.offset(900.dp, 1036.dp).width(752.dp),
            style = JhButtonStyle.Basic, enabled = parked,
        )
    }
}

@Composable
private fun CheckRow(
    icon: ImageVector, title: String, tag: String, status: String, ok: Boolean,
    action: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().height(112.dp).clip(shape).background(JhColor.ButtonBasic)
            .border(1.dp, JhColor.DividerSecondary, shape).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = JhColor.TextPrimary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = JhType.BodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x1FFFFFFF)).padding(horizontal = 10.dp, vertical = 2.dp)) {
                Text(tag, style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.TextTertiary)
            }
        }
        if (action != null) action() else {
            Text(status, style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = if (ok) JhColor.TextSecondary else JhColor.Amber)
            Spacer(Modifier.width(16.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (ok) Color(0x33607890) else Color(0x1FFFFFFF)),
                contentAlignment = Alignment.Center,
            ) { if (ok) Icon(pleos.jamhang.ui.theme.PlIcons.Check, null, tint = JhColor.Steel, modifier = Modifier.size(28.dp)) }
        }
    }
}

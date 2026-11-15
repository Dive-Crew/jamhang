package pleos.jamhang.ui.testmenu

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pleos.jamhang.AppContainer
import pleos.jamhang.session.PauseReason
import pleos.jamhang.vehicle.GearState
import pleos.jamhang.ui.components.AmbientBackground
import pleos.jamhang.ui.components.JhHeader
import pleos.jamhang.ui.theme.JhColor
import pleos.jamhang.ui.theme.JhType

/**
 * 테스트 메뉴 "상황을 바꿔서 확인하기".
 * 에뮬레이터에서 기어·문·주행 규제·오디오 중단·내비 유무를 바꿔 각 예외 화면을 시연한다.
 * 진입: 홈 "테스트" 버튼 / 앱 아이콘 길게 / 04 끝나는 시각 칩 길게.
 */
@Composable
fun TestMenuScreen(c: AppContainer, onBack: () -> Unit) {
    val fakeOn by c.vehicle.fakeEnabled.collectAsStateWithLifecycle()
    val fake by c.vehicle.fake.collectAsStateWithLifecycle()
    val real by c.vehicle.state.collectAsStateWithLifecycle()
    val restrictFake by c.restriction.fakeOverride.collectAsStateWithLifecycle()
    val naviOverride by c.navi.testOverride.collectAsStateWithLifecycle()
    val consent by c.session.consent.collectAsStateWithLifecycle()
    val pname by c.profile.name.collectAsStateWithLifecycle()
    val pguest by c.profile.guest.collectAsStateWithLifecycle()
    val session by c.session.state.collectAsStateWithLifecycle()
    val active = c.session.active

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(Modifier.offset(48.dp, 40.dp).width(1604.dp)) {
            JhHeader("상황을 바꿔서 확인하기", onBack)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().height(1000.dp).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                Column(Modifier.weight(1f)) {
                    Section("차량 상태")
                    Text("현재: 기어 ${real.gear} (${if (real.gearReadable) "읽음" else "못 읽음"}) · 문 잠김 ${real.doorsLocked ?: "미지원"} · 운전석 문 열림 ${real.driverDoorOpen ?: "미지원"} · 충전 ${real.charging ?: "미지원"} · 배터리 ${real.batteryPercent?.let { "$it%" } ?: "모름"}",
                        style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.TextTertiary)
                    Spacer(Modifier.height(12.dp))
                    Options("값 출처", listOf("실제 차량" to !fakeOn, "가짜 값" to fakeOn)) { c.vehicle.setFakeEnabled(it == 1) }
                    Options(
                        "기어 (가짜)", GearState.entries.filter { it != GearState.UNKNOWN }.map { it.name to (fake.gear == it) },
                        enabled = fakeOn,
                    ) { i -> c.vehicle.updateFake { it.copy(gear = GearState.entries.filter { g -> g != GearState.UNKNOWN }[i]) } }
                    Options("기어 읽기 (가짜)", listOf("가능" to fake.gearReadable, "불가" to !fake.gearReadable), enabled = fakeOn) { i ->
                        c.vehicle.updateFake { it.copy(gearReadable = i == 0) }
                    }
                    Options("문 잠금 (가짜)", listOf("잠김" to (fake.doorsLocked == true), "열림" to (fake.doorsLocked == false), "미지원" to (fake.doorsLocked == null)), enabled = fakeOn) { i ->
                        c.vehicle.updateFake { it.copy(doorsLocked = listOf(true, false, null)[i]) }
                    }
                    Options("운전석 문 (가짜)", listOf("닫힘" to (fake.driverDoorOpen == false), "열림" to (fake.driverDoorOpen == true)), enabled = fakeOn) { i ->
                        c.vehicle.updateFake { it.copy(driverDoorOpen = i == 1) }
                    }
                    Options("충전 (가짜)", listOf("안 함" to (fake.charging == false), "충전 중" to (fake.charging == true), "미지원" to (fake.charging == null)), enabled = fakeOn) { i ->
                        c.vehicle.updateFake { it.copy(charging = listOf(false, true, null)[i]) }
                    }
                    Options("배터리 (가짜)", listOf(18, 64, 92).map { "$it%" to (fake.batteryPercent == it) }, enabled = fakeOn) { i ->
                        c.vehicle.updateFake { it.copy(batteryPercent = listOf(18, 64, 92)[i]) }
                    }
                    Options("주행 규제", listOf("실제" to (restrictFake == null), "규제 없음" to (restrictFake == false), "규제 중" to (restrictFake == true))) { i ->
                        c.restriction.setFake(listOf(null, false, true)[i])
                    }
                }
                Column(Modifier.weight(1f)) {
                    Section("잠항 중 이벤트 ${if (active != null) "(진행 중)" else "(잠항 중에만)"}")
                    Actions(
                        enabled = active != null,
                        "종료 1분 전으로 건너뛰기" to { c.session.skipToWarning() },
                        "전화 수신 (오디오 잠시 끊김)" to { c.session.pause(PauseReason.AUDIO) },
                        "전화 끝남 (오디오 복귀)" to { c.session.pauseCauseCleared(PauseReason.AUDIO) },
                        "다른 미디어 재생 (소리 꺼짐)" to { c.session.setSoundOn(false) },
                    )
                    Spacer(Modifier.height(24.dp))
                    Section("기록 · 홈")
                    Options("기록 저장 동의", listOf("동의" to consent, "동의 안 함" to !consent)) { c.session.setConsent(it == 0) }
                    Options("프로필 이름", listOf("시스템" to (pname == null || pname !in listOf("지예", "민준")), "지예" to (pname == "지예"), "민준" to (pname == "민준"))) {
                        c.profile.setNameForTest(listOf(null, "지예", "민준")[it])
                    }
                    Options("게스트 프로필", listOf("아님" to !pguest, "게스트" to pguest)) { c.profile.setGuestForTest(it == 1) }
                    Actions(
                        enabled = true,
                        "09 저장 동의 다시 묻기" to { c.profile.resetConsentAsked() },
                        "기록 비우기 (10 새 프로필)" to { c.logs.replaceAll(emptyList()) },
                    )
                    Options("내비 연결 (실제: ${if (c.navi.connected) "연결됨" else "없음"})", listOf("실제" to (naviOverride == null), "있음" to (naviOverride == true), "없음" to (naviOverride == false))) { c.navi.setAvailableForTest(listOf(null, true, false)[it]) }
                    Actions(
                        enabled = session !is pleos.jamhang.session.SessionState.Active,
                        "시연 데이터로 되돌리기 (어제까지 36겹)" to { c.resetDemo() },
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = JhType.TitleSmall)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Options(label: String, options: List<Pair<String, Boolean>>, enabled: Boolean = true, onPick: (Int) -> Unit) {
    Column(Modifier.padding(bottom = 20.dp)) {
        Text(label, style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = if (enabled) JhColor.TextSecondary else JhColor.TextQuaternary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEachIndexed { i, (text, sel) ->
                Box(
                    Modifier.height(72.dp).clip(RoundedCornerShape(20.dp))
                        .background(if (sel) Color(0xFFE0E1E6) else JhColor.ButtonBasic)
                        .clickable(enabled = enabled) { onPick(i) }
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(text, style = JhType.BodyMedium, color = if (sel) JhColor.TextOnFilled else if (enabled) JhColor.TextPrimary else JhColor.TextQuaternary) }
            }
        }
    }
}

@Composable
private fun Actions(enabled: Boolean, vararg actions: Pair<String, () -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.forEach { (text, run) ->
            Box(
                Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(20.dp)).background(JhColor.ButtonBasic)
                    .clickable(enabled = enabled, onClick = run).padding(horizontal = 28.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text(text, style = JhType.BodyMedium, color = if (enabled) JhColor.TextPrimary else JhColor.TextQuaternary) }
        }
    }
}

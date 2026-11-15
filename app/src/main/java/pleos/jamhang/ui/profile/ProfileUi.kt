package pleos.jamhang.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pleos.jamhang.AppContainer
import pleos.jamhang.data.DiveLogStore
import pleos.jamhang.profile.SuggestSettings
import pleos.jamhang.profile.TimeSlot
import pleos.jamhang.ui.components.JhButton
import pleos.jamhang.ui.components.JhButtonStyle
import pleos.jamhang.ui.components.JhPopup
import pleos.jamhang.ui.components.PopupTwoButtons
import pleos.jamhang.ui.theme.JhColor
import pleos.jamhang.ui.theme.JhType
import java.time.DayOfWeek

private val Cap = JhType.Caption.copy(fontWeight = FontWeight.Normal)
private val Danger = Color(0xFFFF7A6E)

/** 홈 우상단 프로필 버튼 이름: "지예 프로필" / "게스트" / "프로필" */
fun profileLabel(name: String?, guest: Boolean) = when {
    guest -> "게스트"
    name != null -> "$name 프로필"
    else -> "프로필"
}

/** 09 첫 실행 · 기록 저장 동의 */
@Composable
fun ConsentPopup(name: String?, onSave: () -> Unit, onSkip: () -> Unit, onPolicy: () -> Unit) {
    JhPopup(onDismiss = null, width = 1000.dp, padding = androidx.compose.foundation.layout.PaddingValues(48.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("휴식을 기록할까요?", style = JhType.TitleMedium)
            Spacer(Modifier.height(12.dp))
            Text("${name ?: "이"} 프로필에 저장해요.", style = JhType.BodyMediumRegular, color = JhColor.TextTertiary)
            Spacer(Modifier.height(36.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0x14FFFFFF))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                ConsentRow(true, "휴식 시간과 수심, 메모만 저장해요")
                ConsentRow(false, "위치와 주행 기록, 음성은 저장하지 않아요")
                ConsentRow(false, "광고나 마케팅에 쓰지 않아요")
            }
            Spacer(Modifier.height(40.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                JhButton("저장 없이 쉬기", onSkip, Modifier.weight(1f))
                JhButton("이 프로필에 저장", onSave, Modifier.weight(1f), style = JhButtonStyle.Filled)
            }
            Spacer(Modifier.height(24.dp))
            Text("개인정보 처리방침 ›", style = Cap, color = JhColor.TextTertiary, modifier = Modifier.clickable(onClick = onPolicy).padding(8.dp))
        }
    }
}

@Composable
private fun ConsentRow(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) pleos.jamhang.ui.theme.PlIcons.Check else pleos.jamhang.ui.theme.PlIcons.Close, null, tint = if (ok) JhColor.Mint else JhColor.TextTertiary, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(20.dp))
        Text(text, style = JhType.BodyMediumRegular, color = JhColor.TextSecondary)
    }
}

/**
 * 12 프로필과 기록 (오른쪽 시트) → 22 주말 항해 제안 설정 · 13 기록 삭제 확인.
 * @param onSaved 저장된 기록 보기 → 23-M
 */
@Composable
fun BoxScope.ProfileSheet(c: AppContainer, onClose: () -> Unit, onSaved: () -> Unit, onToast: (String) -> Unit) {
    val name by c.profile.name.collectAsStateWithLifecycle()
    val guest by c.profile.guest.collectAsStateWithLifecycle()
    val consent by c.session.consent.collectAsStateWithLifecycle()
    val memoOn by c.profile.memoOn.collectAsStateWithLifecycle()
    val suggest by c.profile.suggest.collectAsStateWithLifecycle()
    val logs by c.logs.logs.collectAsStateWithLifecycle()
    val layerCount = remember(logs) { DiveLogStore.layers(logs).size }
    var page by remember { mutableStateOf(0) } // 0 = 프로필과 기록, 1 = 주말 항해 제안
    var confirmDelete by remember { mutableStateOf(false) }

    // 시트 바깥을 누르면 닫힘
    Box(
        Modifier.fillMaxSize().background(Color(0x99131417))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
    )
    Column(
        Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 24.dp).width(820.dp).height(1136.dp)
            .clip(RoundedCornerShape(40.dp)).background(Color(0xFF2A2C31))
            .border(1.dp, JhColor.DividerSecondary, RoundedCornerShape(40.dp))
            .clickable(remember { MutableInteractionSource() }, null) {}
            .padding(horizontal = 40.dp, vertical = 32.dp),
    ) {
        if (page == 0) {
            Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("프로필과 기록", style = JhType.TitleSmall, modifier = Modifier.weight(1f))
                RoundIcon(pleos.jamhang.ui.theme.PlIcons.Close, "닫기", onClose)
            }
            Spacer(Modifier.height(20.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // 프로필 카드
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0x14FFFFFF)).padding(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(88.dp).clip(CircleShape).background(Color(0xFF6B7380)), contentAlignment = Alignment.Center) {
                        Text(if (guest) "G" else (name?.take(1) ?: "?"), style = JhType.TitleSmall)
                    }
                    Spacer(Modifier.width(28.dp))
                    Column {
                        Text(if (guest) "게스트" else (name ?: "내 프로필"), style = JhType.BodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(if (guest) "게스트는 기록을 남기지 않아요" else "현재 사용 중인 Pleos 프로필", style = Cap, color = JhColor.TextTertiary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Pleos 프로필 설정 ›", style = Cap, color = JhColor.TextSecondary,
                            modifier = Modifier.clickable { onToast("Pleos 설정의 프로필에서 바꿀 수 있어요") },
                        )
                    }
                }
                SectionLabel("기록")
                ToggleRow("지층 저장", "이 프로필에 휴식 시간과 수심 저장", consent && !guest, enabled = !guest) { c.session.setConsent(it) }
                ToggleRow("한 줄 메모 저장", "쉬고 나서 남긴 메모", memoOn && !guest, enabled = !guest && consent) { c.profile.setMemoOn(it) }
                SectionLabel("제안")
                NavRow("주말 항해 제안", if (suggest.on) "켜짐" else "꺼짐") { page = 1 }
                SectionLabel("내 데이터")
                NavRow("저장된 기록 보기", "${layerCount}겹", onSaved)
                Spacer(Modifier.height(24.dp))
                Text(
                    "이 프로필의 기록 모두 지우기", style = JhType.BodyMedium, color = if (layerCount > 0) Danger else JhColor.TextQuaternary,
                    modifier = Modifier.clickable(enabled = layerCount > 0) { confirmDelete = true }.padding(vertical = 12.dp),
                )
                Spacer(Modifier.height(24.dp))
                Text("위치·주행 기록·음성은 저장하지 않고, 광고나 마케팅에 쓰지 않아요. 개인정보 처리방침", style = Cap, color = JhColor.TextQuaternary)
                Spacer(Modifier.height(12.dp))
                // OpenRAIL-M(Supertonic 3) 이용 조건: 기계가 만든 음성임을 밝힌다
                Text("안내 음성은 AI 합성 음성입니다 (호흡 안내 포함 · Supertonic 3)", style = Cap, color = JhColor.TextQuaternary)
            }
        } else {
            SuggestPage(suggest, onBack = { page = 0 }) { c.profile.setSuggest(it); page = 0; onToast("주말 항해 제안을 저장했어요") }
        }
    }

    // 13 기록 삭제 확인
    if (confirmDelete) {
        JhPopup(onDismiss = { confirmDelete = false }) {
            PopupTwoButtons(
                "지층 ${layerCount}겹을 모두 지울까요?",
                primary = "지우기", onPrimary = { confirmDelete = false; c.logs.replaceAll(emptyList()); onToast("기록을 모두 지웠어요") },
                secondary = "취소", onSecondary = { confirmDelete = false },
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "${name ?: "이"} 프로필의 기록만 지워지고, 되돌릴 수 없어요.", style = JhType.BodyMediumRegular,
                    color = JhColor.TextTertiary, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 22 주말 항해 제안 설정 */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SuggestPage(initial: SuggestSettings, onBack: () -> Unit, onSave: (SuggestSettings) -> Unit) {
    var s by remember { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(pleos.jamhang.ui.theme.PlIcons.ArrowLeft, "뒤로", onBack)
        Spacer(Modifier.width(20.dp))
        Text("주말 항해 제안", style = JhType.TitleSmall)
    }
    Spacer(Modifier.height(16.dp))
    Text("휴식을 4번 기록하면 드라이브 코스를 추천해요.\n선택한 시간에 홈을 열면 볼 수 있어요.", style = JhType.BodyMediumRegular, color = JhColor.TextTertiary)
    Spacer(Modifier.height(16.dp))
    ToggleRow("제안 받기", "드라이브 코스 추천받기", s.on) { s = s.copy(on = it) }
    SectionLabel("요일")
    val days = listOf("월", "화", "수", "목", "금", "토", "일")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DayOfWeek.entries.forEachIndexed { i, d ->
            Chip(days[i], d in s.days, s.on, Modifier.weight(1f)) {
                s = s.copy(days = if (d in s.days) s.days - d else s.days + d)
            }
        }
    }
    SectionLabel("시간대")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeSlot.entries.forEach { t ->
            Chip(t.label, t in s.slots, s.on, Modifier.weight(1f)) {
                s = s.copy(slots = if (t in s.slots) s.slots - t else s.slots + t)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Text("여러 요일과 시간대를 함께 고를 수 있어요.", style = Cap, color = JhColor.TextQuaternary)
    Spacer(Modifier.weight(1f))
    val valid = !s.on || (s.days.isNotEmpty() && s.slots.isNotEmpty())
    JhButton("저장", { onSave(s) }, Modifier.fillMaxWidth(), style = JhButtonStyle.Filled, enabled = s != initial && valid)
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(36.dp))
    Text(text, style = Cap, color = JhColor.TextTertiary)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = JhType.BodyLarge, color = if (enabled) JhColor.TextPrimary else JhColor.TextQuaternary)
            Spacer(Modifier.height(4.dp))
            Text(sub, style = Cap, color = JhColor.TextTertiary)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF5FCB7E), checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0x33FFFFFF), uncheckedThumbColor = JhColor.TextSecondary,
                uncheckedBorderColor = Color.Transparent,
            ),
            modifier = Modifier.padding(end = 16.dp).graphicsLayer { scaleX = 1.4f; scaleY = 1.4f },
        )
    }
}

@Composable
private fun NavRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(88.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = JhType.BodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = Cap, color = JhColor.TextTertiary)
        Icon(pleos.jamhang.ui.theme.PlIcons.ArrowRight, null, tint = JhColor.TextTertiary, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(72.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected && enabled) Color(0xFFE0E1E6) else Color(0x14FFFFFF))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = JhType.Caption,
            color = when { !enabled -> JhColor.TextQuaternary; selected -> JhColor.TextOnFilled; else -> JhColor.TextSecondary },
        )
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(Color(0x1FFFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = JhColor.TextPrimary, modifier = Modifier.size(36.dp)) }
}


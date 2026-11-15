package pleos.jamhang.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.EvStation
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pleos.jamhang.AppContainer
import pleos.jamhang.DivePlan
import pleos.jamhang.R
import pleos.jamhang.data.DiveLogStore
import pleos.jamhang.ui.components.AmbientBackground
import pleos.jamhang.ui.components.IconBox
import pleos.jamhang.ui.components.JhButton
import pleos.jamhang.ui.components.JhButtonSize
import pleos.jamhang.ui.components.JhButtonStyle
import pleos.jamhang.ui.components.JhCard
import pleos.jamhang.ui.components.JhToast
import pleos.jamhang.ui.components.Stat
import pleos.jamhang.ui.components.StatRow
import pleos.jamhang.ui.components.StrataPanel
import pleos.jamhang.ui.components.formatDepth
import pleos.jamhang.ui.theme.JhColor
import pleos.jamhang.ui.theme.JhType
import pleos.jamhang.ui.profile.ProfileSheet
import java.time.YearMonth

/** 01 홈 · 수면 위 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    c: AppContainer,
    plan: DivePlan,
    onQuickStart: () -> Unit,
    onPrep: () -> Unit,
    onStrata: () -> Unit,
    onTestMenu: () -> Unit,
    onSavedRecords: () -> Unit,
) {
    val logs by c.logs.logs.collectAsStateWithLifecycle()
    val layers = remember(logs) { DiveLogStore.layers(logs) }
    val depth = remember(logs) { DiveLogStore.depthOf(logs) }
    val (monthCount, monthMin) = remember(logs) { DiveLogStore.monthStats(logs) }
    val name by c.profile.name.collectAsStateWithLifecycle()
    val guest by c.profile.guest.collectAsStateWithLifecycle()
    val consent by c.session.consent.collectAsStateWithLifecycle()
    val consentAsked by c.profile.consentAsked.collectAsStateWithLifecycle()
    val suggest by c.profile.suggest.collectAsStateWithLifecycle()
    var suggestClosed by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    val saving = consent && !guest
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(2500); toast = null } }

    // 주말 항해 제안 (22): 휴식 4번 이상 + 제안 켬 + 고른 요일·시간대. 내비가 없으면 [코스 보기]는 안내 토스트
    val showSuggest = saving && !suggestClosed && logs.size >= 4 && suggest.matches(java.time.LocalDateTime.now())

    // 충전 대기 휴식 제안 (Vehicle SDK EvBattery · CAR_ENERGY): 충전 중이면 바다 코스 제안보다 먼저 보인다.
    // 닫으면 이번 충전 동안은 다시 띄우지 않고, 충전이 끝나면 다음 충전에 다시 보인다.
    val vehicle by c.vehicle.state.collectAsStateWithLifecycle()
    var chargeClosed by remember { mutableStateOf(false) }
    LaunchedEffect(vehicle.charging) { if (vehicle.charging != true) chargeClosed = false }
    val showCharge = vehicle.charging == true && !chargeClosed
    val chargeCard: @Composable () -> Unit = {
        ChargeSuggestCard(
            percent = vehicle.batteryPercent, minutes = plan.minutes,
            onStart = onQuickStart, onClose = { chargeClosed = true },
        )
        Spacer(Modifier.height(32.dp))
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        StrataPanel(
            layers, depth, Modifier.offset(48.dp, 48.dp), onClick = if (saving && layers.isNotEmpty()) onStrata else null,
            emptyState = when {
                guest -> pleos.jamhang.ui.components.PanelEmpty.Guest
                !consent -> pleos.jamhang.ui.components.PanelEmpty.NotSaving
                else -> pleos.jamhang.ui.components.PanelEmpty.NoLayers
            }.takeIf { !saving || layers.isEmpty() },
            ownerName = name,
            onEmptyAction = { if (guest) toast = "Pleos 설정의 프로필에서 바꿀 수 있어요" else sheetOpen = true },
        )

        Column(Modifier.offset(712.dp, 40.dp).width(940.dp)) {
            // 앱 바: 아이콘 + 잠항 / 프로필
            Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape)
                        .combinedClickable(onClick = {}, onLongClick = onTestMenu),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_brand_symbol), "잠항 (길게 눌러 테스트 메뉴)", Modifier.size(66.24.dp))
                }
                Spacer(Modifier.width(20.dp))
                Text("잠항", style = JhType.TitleMedium)
                Spacer(Modifier.weight(1f))
                JhButton(pleos.jamhang.ui.profile.profileLabel(name, guest), { sheetOpen = true }, size = JhButtonSize.Medium)
            }
            Spacer(Modifier.height(40.dp))
            val first = saving && logs.isEmpty()
            Text(if (name != null && !guest) "${name}님, 반가워요" else "반가워요", style = JhType.BodyMediumRegular, color = JhColor.TextSecondary)
            Spacer(Modifier.height(8.dp))
            Text(if (first) "첫 휴식을 시작할까요?" else "잠깐 쉬어 갈까요?", style = JhType.HeadlineMedium)
            Spacer(Modifier.height(48.dp))

            // 최근 프리셋 카드 → 바로 쉬기 (홈에서 휴식까지 탭 2번: 바로 쉬기 → 해치 자동 진행)
            JhCard(Modifier.fillMaxWidth().height(152.dp)) {
                Row(Modifier.fillMaxSize().padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBox(Icons.Rounded.History)
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (first) "추천 시간" else if (guest) "가볍게" else "지난번처럼", style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.TextTertiary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${plan.cabin.title}에서 ${plan.minutes}분" + if (plan.breathGuide) " · 호흡 안내" else "",
                            style = JhType.TitleSmall,
                        )
                    }
                    JhButton("바로 쉬기", onQuickStart, style = JhButtonStyle.Filled, size = JhButtonSize.Medium)
                }
            }
            Spacer(Modifier.height(28.dp))
            if (first || !saving) {
                // 10 새 프로필 / 11 게스트: 통계 대신 저장 안내 한 줄
                Row(
                    Modifier.fillMaxWidth().height(88.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .background(androidx.compose.ui.graphics.Color(0x0DFFFFFF)).padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Lock, null, tint = JhColor.TextTertiary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        when {
                            guest -> "게스트는 기록을 남기지 않아요"
                            !consent -> "이 프로필은 기록을 저장하지 않아요"
                            else -> "${name ?: "이"} 프로필에 저장된 기록이에요"
                        },
                        style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.TextSecondary,
                    )
                }
                Spacer(Modifier.height(32.dp))
                if (showCharge) chargeCard() else Spacer(Modifier.height(240.dp))
                JhButton("시간 정하기", onPrep, Modifier.fillMaxWidth())
                return@Column
            }
            StatRow(
                listOf(
                    Stat("${YearMonth.now().monthValue}월 기록", "${monthCount}번"),
                    Stat("휴식 시간", "${monthMin}분"),
                    Stat("누적 수심", formatDepth(depth)),
                ),
            )
            Spacer(Modifier.height(32.dp))
            if (showCharge) {
                chargeCard()
            } else if (showSuggest) {
                JhCard(Modifier.fillMaxWidth().height(208.dp)) {
                    Row(Modifier.fillMaxSize().padding(28.dp)) {
                        IconBox(Icons.Rounded.Route)
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f).padding(top = 2.dp)) {
                            Text("그동안 ${logs.size.coerceAtMost(99)}번 쉬었어요", style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.Amber)
                            Spacer(Modifier.height(6.dp))
                            Text("바다 보러 달려볼까요?", style = JhType.TitleSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 20.dp, end = 16.dp)) {
                            JhButton("코스 보기", { toast = c.navi.sendSeaCourse() }, size = JhButtonSize.Medium)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "제안 끄기", style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.TextTertiary,
                                modifier = Modifier.clickable {
                                    c.profile.setSuggest(suggest.copy(on = false))
                                    toast = "프로필과 기록에서 다시 켤 수 있어요"
                                },
                            )
                        }
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(12.dp).size(56.dp).clip(CircleShape)
                            .clickable { suggestClosed = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(pleos.jamhang.ui.theme.PlIcons.Close, "이번 제안 닫기", tint = JhColor.TextSecondary, modifier = Modifier.size(32.dp)) }
                }
                Spacer(Modifier.height(32.dp))
            } else {
                Spacer(Modifier.height(240.dp))
            }
            JhButton("시간 정하기", onPrep, Modifier.fillMaxWidth())
        }
        if (sheetOpen) {
            ProfileSheet(
                c, onClose = { sheetOpen = false },
                onSaved = { sheetOpen = false; onSavedRecords() },
                onToast = { toast = it },
            )
        }
        // 09 첫 실행: 기록 저장 동의 (게스트는 묻지 않음)
        if (!consentAsked && !guest) {
            pleos.jamhang.ui.profile.ConsentPopup(
                name,
                onSave = { c.session.setConsent(true); c.profile.markConsentAsked() },
                onSkip = { c.session.setConsent(false); c.profile.markConsentAsked() },
                onPolicy = { toast = "위치·주행 기록·음성은 저장하지 않아요" },
            )
        }
        JhToast(toast)
    }
}

/** 충전 대기 휴식 제안 카드 — 바다 코스 카드와 같은 틀 (208 높이, 아이콘 + 두 줄 + 버튼 + 닫기) */
@Composable
private fun ChargeSuggestCard(percent: Int?, minutes: Int, onStart: () -> Unit, onClose: () -> Unit) {
    JhCard(Modifier.fillMaxWidth().height(208.dp)) {
        Row(Modifier.fillMaxSize().padding(28.dp)) {
            IconBox(pleos.jamhang.ui.theme.PlIcons.Charging)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text(
                    "충전 중" + (percent?.let { " · $it%" } ?: ""),
                    style = JhType.Caption.copy(fontWeight = FontWeight.Normal), color = JhColor.Mint,
                )
                Spacer(Modifier.height(6.dp))
                Text("충전하는 동안 쉬어 갈까요?", style = JhType.TitleSmall)
            }
            JhButton(
                "${minutes}분 쉬기", onStart, style = JhButtonStyle.Filled, size = JhButtonSize.Medium,
                modifier = Modifier.padding(top = 36.dp, end = 16.dp),
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp).size(56.dp).clip(CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(pleos.jamhang.ui.theme.PlIcons.Close, "이번 충전 동안 제안 닫기", tint = JhColor.TextSecondary, modifier = Modifier.size(32.dp)) }
    }
}

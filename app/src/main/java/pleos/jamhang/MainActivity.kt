package pleos.jamhang

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pleos.jamhang.data.Cabin
import pleos.jamhang.session.DiveService
import pleos.jamhang.session.SessionState
import pleos.jamhang.ui.diving.DivingScreen
import pleos.jamhang.ui.hatch.HatchScreen
import pleos.jamhang.ui.home.HomeScreen
import pleos.jamhang.ui.prep.PrepScreen
import pleos.jamhang.ui.result.ResultScreen
import pleos.jamhang.ui.strata.StrataScreen
import pleos.jamhang.ui.testmenu.TestMenuScreen
import pleos.jamhang.ui.theme.JamhangTheme
import pleos.jamhang.ui.theme.JhColor

object Routes {
    const val HOME = "home"
    const val PREP = "prep"
    const val HATCH = "hatch"
    const val DIVE = "dive"
    const val RESULT = "result"
    const val STRATA = "strata"
    const val SAVED = "saved"
    const val TEST = "test"
}

/** 02에서 고른 잠항 설정. 홈 "바로 쉬기"는 지난 설정을 그대로 쓴다 */
class DivePlan(minutes: Int, cabin: Cabin, breathGuide: Boolean) {
    var minutes by mutableStateOf(minutes)
    var cabin by mutableStateOf(cabin)
    var memo by mutableStateOf("")
    var breathGuide by mutableStateOf(breathGuide)
}

class MainActivity : ComponentActivity() {

    private val container get() = (application as JamhangApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)

        // 프로세스가 다시 만들어졌는데 잠항이 진행 중이면 서비스 재개
        if (container.session.active != null) DiveService.start(this)

        setContent {
            JamhangTheme {
                Box(Modifier.fillMaxSize().background(JhColor.BackgroundPrimary)) {
                    JamhangNav(container)
                }
            }
        }
    }
}

@Composable
private fun JamhangNav(c: AppContainer) {
    val nav = rememberNavController()
    val prefs = c.prefs
    val plan = remember {
        DivePlan(
            prefs.getInt("lastMinutes", 10),
            runCatching { Cabin.valueOf(prefs.getString("lastCabin", null) ?: "") }.getOrDefault(Cabin.LIGHT_SEA),
            prefs.getBoolean("lastBreath", false),
        )
    }
    val sessionState by c.session.state.collectAsStateWithLifecycle()

    // 세션 상태가 화면을 정한다: 진행 중 → 04, 끝남 → 결과
    LaunchedEffect(sessionState is SessionState.Active, sessionState is SessionState.Ended) {
        when (sessionState) {
            is SessionState.Active -> nav.goSingle(Routes.DIVE)
            is SessionState.Ended -> nav.goSingle(Routes.RESULT)
            SessionState.Idle -> Unit
        }
    }

    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                c = c, plan = plan,
                onQuickStart = { nav.go(Routes.HATCH) },
                onPrep = { nav.go(Routes.PREP) },
                onStrata = { nav.go(Routes.STRATA) },
                onTestMenu = { nav.go(Routes.TEST) },
                onSavedRecords = { nav.go(Routes.SAVED, force = true) },
            )
        }
        composable(Routes.PREP) {
            PrepScreen(c = c, plan = plan, onBack = { nav.back() }, onStart = { nav.go(Routes.HATCH) })
        }
        composable(Routes.HATCH) {
            HatchScreen(
                c = c, onBack = { nav.back() },
                onDive = {
                    prefs.edit().putInt("lastMinutes", plan.minutes).putString("lastCabin", plan.cabin.name)
                        .putBoolean("lastBreath", plan.breathGuide).apply()
                    c.session.start(plan.minutes, plan.cabin, plan.memo, plan.breathGuide)
                    plan.memo = ""
                    DiveService.start(nav.context)
                },
            )
        }
        composable(Routes.DIVE) { DivingScreen(c = c, onTestMenu = { nav.go(Routes.TEST) }) }
        composable(Routes.RESULT) {
            ResultScreen(
                c = c,
                onHome = { c.session.acknowledgeResult(); nav.goHome() },
                onStrata = { c.session.acknowledgeResult(); nav.goHome(); nav.go(Routes.STRATA, force = true) },
            )
        }
        // 지층 보기의 ‹는 언제나 홈으로 (어떤 경로로 들어왔든)
        composable(Routes.STRATA) { StrataScreen(c = c, onBack = { nav.goHome() }) }
        // 12 프로필과 기록 → 저장된 기록 보기 (23-M부터)
        composable(Routes.SAVED) { StrataScreen(c = c, onBack = { nav.goHome() }, startAll = true) }
        composable(Routes.TEST) { TestMenuScreen(c = c, onBack = { nav.back() }) }
    }
}

/**
 * 화면 전환 중(현재 화면이 아직 RESUMED가 아닐 때) 들어온 중복 탭은 버린다.
 * 그렇지 않으면 같은 화면이 여러 겹 쌓여 ‹를 눌러도 안 돌아가는 것처럼 보인다.
 */
private fun NavHostController.go(route: String, force: Boolean = false) {
    if (!force && currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
    navigate(route) { launchSingleTop = true }
}

private fun NavHostController.back() {
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
    popBackStack()
}

private fun NavHostController.goSingle(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { popUpTo(Routes.HOME) }
}

private fun NavHostController.goHome() {
    navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
}

package pleos.jamhang.navi

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 주말 항해 → 바다 코스를 기본 내비로 전달 (NaviHelper SDK, NAVI_ROUTE_SEARCH).
 * available = 내비 서비스에 연결됨. 테스트 메뉴에서 있음/없음으로 덮어쓸 수 있다.
 */
class NaviBridge(context: Context, scope: CoroutineScope) {
    private val navi = PleosNavi(context)
    private val override = MutableStateFlow<Boolean?>(null)
    val testOverride: StateFlow<Boolean?> = override.asStateFlow()

    val available: StateFlow<Boolean> =
        combine(navi.state, override) { s, o -> o ?: s.connected }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /** 실제 내비에 연결됐는지 (테스트 덮어쓰기와 무관) */
    val connected: Boolean get() = navi.state.value.connected

    fun start() = navi.start()

    /** null = 실제 연결 상태를 따름 */
    fun setAvailableForTest(on: Boolean?) {
        override.value = on
    }

    /** 가장 가까운 바다로 안내를 시작하고 내비 화면을 띄운다. @return 사용자에게 보일 결과 문구 */
    fun sendSeaCourse(): String {
        val s = navi.state.value
        val target = PleosNavi.nearestSea(s.lat, s.lng)
        if (!s.connected) return "내비가 연결되면 ${target.name}까지 안내해 드려요"
        if (!navi.startGuidance(target)) return "내비에 길 안내를 보내지 못했어요"
        navi.showNavigation()
        return "${target.name}까지 길 안내를 시작했어요"
    }
}

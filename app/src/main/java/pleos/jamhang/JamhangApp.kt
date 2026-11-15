package pleos.jamhang

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import pleos.jamhang.car.DrivingRestriction
import pleos.jamhang.data.DemoSeed
import pleos.jamhang.data.DiveLogStore
import pleos.jamhang.navi.NaviBridge
import pleos.jamhang.session.DiveSessionController
import pleos.jamhang.vehicle.PleosVehicleSource
import pleos.jamhang.vehicle.SwitchableVehicleSource

/** 수동 DI. 공모전 규모라 Hilt 없이 앱 단위 싱글턴으로 둔다. */
class AppContainer(context: Context, scope: CoroutineScope) {
    val vehicle = SwitchableVehicleSource(PleosVehicleSource(context), scope)
    val restriction = DrivingRestriction(context)
    val navi = NaviBridge(context, scope)
    val voice = pleos.jamhang.voice.VoiceInput(context)
    val logs = DiveLogStore(context)
    val session = DiveSessionController(context, logs)
    /** 사운드 v2 재생기. 효과음을 앱 시작 때 미리 불러 둔다 */
    val sound = pleos.jamhang.session.SoundEngine(context)
    val prefs = context.getSharedPreferences("jamhang", Context.MODE_PRIVATE)
    val profile = pleos.jamhang.profile.ProfileStore(context, prefs).also { p -> session.isGuest = { p.guest.value } }

    /** 시연 데이터로 되돌리기 (테스트 메뉴) */
    fun resetDemo() {
        logs.replaceAll(DemoSeed.build())
        prefs.edit().putBoolean("seeded", true).putBoolean("suggestOff", false).apply()
    }
}

class JamhangApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, MainScope())
        if (!container.prefs.getBoolean("seeded", false)) container.resetDemo()
        container.vehicle.start()
        container.restriction.start()
        container.navi.start()
    }
}

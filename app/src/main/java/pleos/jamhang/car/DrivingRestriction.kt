package pleos.jamhang.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CarUxRestrictionsManager 래핑.
 * restricted = 주행 규제 중(화면 제한). 키보드(메모)·설정은 규제 중 금지.
 */
class DrivingRestriction(context: Context) {

    private val appContext = context.applicationContext
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    private val _real = MutableStateFlow(false)
    private val _fakeOverride = MutableStateFlow<Boolean?>(null)
    private val _restricted = MutableStateFlow(false)
    val restricted: StateFlow<Boolean> = _restricted.asStateFlow()
    val fakeOverride: StateFlow<Boolean?> = _fakeOverride.asStateFlow()

    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r ->
        _real.value = r.isRequiresDistractionOptimization
        publish()
    }

    fun start() {
        if (car != null) return
        runCatching {
            val c = Car.createCar(appContext)
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            manager = m
            _real.value = m.currentCarUxRestrictions?.isRequiresDistractionOptimization ?: false
            m.registerListener(listener)
            publish()
        }.onFailure { Log.w("DrivingRestriction", "Car API unavailable", it) }
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    /** 테스트 메뉴: null = 실제 값 사용 */
    fun setFake(value: Boolean?) {
        _fakeOverride.value = value
        publish()
    }

    private fun publish() {
        _restricted.value = _fakeOverride.value ?: _real.value
    }

    @Suppress("unused")
    private fun CarUxRestrictions.describe() = "DO=$isRequiresDistractionOptimization active=$activeRestrictions"
}

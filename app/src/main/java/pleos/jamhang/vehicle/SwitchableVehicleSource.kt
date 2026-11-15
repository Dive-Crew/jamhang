package pleos.jamhang.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 테스트 메뉴 "상황을 바꿔서 확인하기"용.
 * fakeEnabled = true 면 실제 차량 값 대신 [fake] 값을 내보낸다.
 */
class SwitchableVehicleSource(
    private val real: VehicleStateSource,
    scope: CoroutineScope,
) : VehicleStateSource {

    private val _fakeEnabled = MutableStateFlow(false)
    val fakeEnabled: StateFlow<Boolean> = _fakeEnabled.asStateFlow()

    private val _fake = MutableStateFlow(
        VehicleState(gear = GearState.PARK, gearReadable = true, doorsLocked = true, driverDoorOpen = false, charging = false, batteryPercent = 64),
    )
    val fake: StateFlow<VehicleState> = _fake.asStateFlow()

    override val state: StateFlow<VehicleState> =
        combine(real.state, _fake, _fakeEnabled) { r, f, on -> if (on) f else r }
            .stateIn(scope, SharingStarted.Eagerly, real.state.value)

    fun setFakeEnabled(on: Boolean) = _fakeEnabled.update { on }
    fun updateFake(block: (VehicleState) -> VehicleState) = _fake.update(block)

    override fun start() = real.start()
    override fun stop() = real.stop()
}

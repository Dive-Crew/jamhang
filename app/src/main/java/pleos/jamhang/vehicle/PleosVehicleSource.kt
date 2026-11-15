package pleos.jamhang.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorLockStateListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Vehicle SDK 2.3.2 구현.
 * 모든 API는 실패할 수 있다고 보고(퍼미션·차종), 실패한 항목은 "미지원(null)"으로 둔다.
 */
class PleosVehicleSource(context: Context) : VehicleStateSource {

    private val appContext = context.applicationContext
    private var vehicle: Vehicle? = null
    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val driverDoor = DoorArea(DoorValue.ROW_1_LEFT)
    private var lockArea: DoorArea? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _state.update { it.copy(gear = gear.toGearState(), gearReadable = true) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "gear listener failed", e)
            _state.update { it.copy(gearReadable = false) }
        }
    }

    private val lockListener = object : DoorLockStateListener {
        override fun onDoorLockStateUpdated(locked: Boolean?) {
            _state.update { it.copy(doorsLocked = locked) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "door lock listener failed", e)
        }
    }

    private val positionListener = object : DoorPositionListener {
        override fun onPositionUpdated(position: Int?) {
            _state.update { it.copy(driverDoorOpen = position?.let { p -> p > 0 }) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "door position listener failed", e)
        }
    }

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(soc: Int?) {
            _state.update { it.copy(batteryPercent = soc) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "soc listener failed", e)
        }
    }

    // 충전 스위치는 리스너가 없어 주기적으로 읽는다 (충전 시작·끝은 분 단위라 20초면 충분)
    private val handler = Handler(Looper.getMainLooper())
    private val chargePoll = object : Runnable {
        override fun run() {
            val v = vehicle ?: return
            runCatching {
                v.getEvBattery().getEvChargeSwitchState(
                    { on -> _state.update { it.copy(charging = on) } },
                    { e -> Log.w(TAG, "charge switch read failed", e); _state.update { it.copy(charging = null) } },
                )
            }.onFailure { Log.w(TAG, "charge api unavailable", it) }
            handler.postDelayed(this, CHARGE_POLL_MS)
        }
    }

    override fun start() {
        if (vehicle != null) return
        val v = runCatching { Vehicle(appContext).also { it.initialize() } }
            .onFailure { Log.w(TAG, "Vehicle init failed", it) }
            .getOrNull() ?: return
        vehicle = v

        runCatching {
            v.getGear().getCurrentGearSelectionState(
                { gear -> _state.update { it.copy(gear = gear.toGearState(), gearReadable = true) } },
                { e -> Log.w(TAG, "gear read failed", e); _state.update { it.copy(gearReadable = false) } },
            )
            v.getGear().registerGearSelection(gearListener)
        }.onFailure { Log.w(TAG, "gear api unavailable", it) }

        // 도어 잠금은 지원 차량에서만 노출 (checkXXXCapabilities 확인 후)
        runCatching {
            v.getDoor().checkDoorLockCapabilities(
                { areas: List<DoorArea> ->
                    // 잠금은 차량이 알려준 영역으로 읽는다 (에뮬레이터는 ROW_1_LEFT 단독 영역을 지원하지 않음)
                    areas.firstOrNull()?.let { area ->
                        lockArea = area
                        v.getDoor().getDoorLockState(area,
                            { locked -> _state.update { it.copy(doorsLocked = locked) } },
                            { e -> Log.w(TAG, "lock read failed", e) })
                        v.getDoor().registerDoorLockState(area, lockListener)
                    }
                },
                { e -> Log.w(TAG, "lock capability failed", e) },
            )
            v.getDoor().getCurrentDoorPosition(driverDoor,
                { pos -> _state.update { it.copy(driverDoorOpen = pos?.let { p -> p > 0 }) } },
                { e -> Log.w(TAG, "door position failed", e) })
            v.getDoor().registerDoorPosition(driverDoor, positionListener)
        }.onFailure { Log.w(TAG, "door api unavailable", it) }

        // 충전 대기 휴식 제안 (CAR_ENERGY). EV가 아니거나 퍼미션이 없으면 null로 남는다
        runCatching {
            v.getEvBattery().getEvBatteryStateOfCharge(
                { soc -> _state.update { it.copy(batteryPercent = soc) } },
                { e -> Log.w(TAG, "soc read failed", e) },
            )
            v.getEvBattery().registerEvBatteryStateOfCharge(socListener)
        }.onFailure { Log.w(TAG, "ev battery api unavailable", it) }
        handler.post(chargePoll)
    }

    override fun stop() {
        val v = vehicle ?: return
        handler.removeCallbacks(chargePoll)
        runCatching { v.getEvBattery().unregisterEvBatteryStateOfCharge(socListener) }
        runCatching { v.getGear().unregisterGearSelection(gearListener) }
        lockArea?.let { a -> runCatching { v.getDoor().unregisterDoorLockState(a, lockListener) } }
        runCatching { v.getDoor().unregisterDoorPosition(driverDoor, positionListener) }
        runCatching { v.release() }
        vehicle = null
    }

    private fun VehicleGear?.toGearState(): GearState = when (this) {
        VehicleGear.GEAR_PARK -> GearState.PARK
        VehicleGear.GEAR_REVERSE -> GearState.REVERSE
        VehicleGear.GEAR_NEUTRAL -> GearState.NEUTRAL
        VehicleGear.GEAR_UNKNOWN, null -> GearState.UNKNOWN
        else -> GearState.DRIVE // D, 1~9단
    }

    private companion object {
        const val TAG = "PleosVehicle"
        const val CHARGE_POLL_MS = 20_000L
    }
}

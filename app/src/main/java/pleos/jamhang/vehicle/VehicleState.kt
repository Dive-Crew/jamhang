package pleos.jamhang.vehicle

import kotlinx.coroutines.flow.StateFlow

enum class GearState { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }

/**
 * 잠항이 보는 차량 상태.
 * - null = 아직 모름 / 이 차량에서 지원하지 않음 (해당 체크 행은 숨긴다)
 */
data class VehicleState(
    val gear: GearState = GearState.UNKNOWN,
    /** Vehicle SDK로 기어를 읽을 수 있는지. false면 해치에서 사용자 확인으로 대체 */
    val gearReadable: Boolean = false,
    /** 도어 잠금 지원 차량이면 true/false, 미지원이면 null */
    val doorsLocked: Boolean? = null,
    /** 운전석 문 열림. 미지원이면 null */
    val driverDoorOpen: Boolean? = null,
    /** 충전 중(충전 스위치 켜짐). CAR_ENERGY 미허용·미지원이면 null */
    val charging: Boolean? = null,
    /** 배터리 잔량 % (SOC). 모르면 null */
    val batteryPercent: Int? = null,
) {
    val isParked: Boolean get() = gear == GearState.PARK
}

interface VehicleStateSource {
    val state: StateFlow<VehicleState>
    fun start()
    fun stop()
}

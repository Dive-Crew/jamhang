package pleos.jamhang.profile

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * 프로필과 기록 설정 (Figma 09–13, 22).
 * 기록은 프로필(Android 사용자)마다 앱 데이터가 따로라서 이 설정도 프로필별로 저장된다.
 * 테스트 메뉴에서 이름·게스트 여부를 바꿔 10/11 화면을 확인할 수 있다.
 */
class ProfileStore(context: Context, private val prefs: SharedPreferences) {

    private val systemName: String? = runCatching {
        context.getSystemService(UserManager::class.java)?.userName?.takeIf { it.isNotBlank() && it != "Owner" && it != "Driver" }
    }.getOrNull()

    private val _name = MutableStateFlow(prefs.getString(K_NAME, null) ?: systemName)
    /** 프로필 이름. 모르면 null → "반가워요"만 */
    val name: StateFlow<String?> = _name.asStateFlow()

    private val _guest = MutableStateFlow(prefs.getBoolean(K_GUEST, false))
    /** 게스트 프로필: 기록을 남기지 않는다 (11) */
    val guest: StateFlow<Boolean> = _guest.asStateFlow()

    private val _consentAsked = MutableStateFlow(prefs.getBoolean(K_ASKED, false))
    /** 09 첫 실행 동의 팝업을 이미 보았는지 */
    val consentAsked: StateFlow<Boolean> = _consentAsked.asStateFlow()

    private val _memoOn = MutableStateFlow(prefs.getBoolean(K_MEMO, true))
    /** 12 "한 줄 메모 저장" */
    val memoOn: StateFlow<Boolean> = _memoOn.asStateFlow()

    private val _suggest = MutableStateFlow(loadSuggest())
    /** 22 주말 항해 제안 설정 */
    val suggest: StateFlow<SuggestSettings> = _suggest.asStateFlow()

    fun markConsentAsked() { _consentAsked.value = true; prefs.edit { putBoolean(K_ASKED, true) } }
    fun setMemoOn(on: Boolean) { _memoOn.value = on; prefs.edit { putBoolean(K_MEMO, on) } }

    fun setSuggest(s: SuggestSettings) {
        _suggest.value = s
        prefs.edit {
            putBoolean(K_SUG_ON, s.on)
            putString(K_SUG_DAYS, s.days.joinToString(",") { it.name })
            putString(K_SUG_SLOTS, s.slots.joinToString(",") { it.name })
        }
    }

    /** 테스트 메뉴: null = 시스템 프로필 이름 */
    fun setNameForTest(n: String?) {
        _name.value = n ?: systemName
        prefs.edit { if (n == null) remove(K_NAME) else putString(K_NAME, n) }
    }

    fun setGuestForTest(on: Boolean) { _guest.value = on; prefs.edit { putBoolean(K_GUEST, on) } }

    /** 테스트 메뉴: 09 동의 팝업을 다시 보기 */
    fun resetConsentAsked() { _consentAsked.value = false; prefs.edit { putBoolean(K_ASKED, false) } }

    private fun loadSuggest() = SuggestSettings(
        on = prefs.getBoolean(K_SUG_ON, !prefs.getBoolean("suggestOff", false)),
        days = prefs.getString(K_SUG_DAYS, null)?.split(',')?.filter { it.isNotEmpty() }?.map { DayOfWeek.valueOf(it) }?.toSet()
            ?: DayOfWeek.entries.toSet(),
        slots = prefs.getString(K_SUG_SLOTS, null)?.split(',')?.filter { it.isNotEmpty() }?.map { TimeSlot.valueOf(it) }?.toSet()
            ?: TimeSlot.entries.toSet(),
    )

    private companion object {
        const val K_NAME = "profileName"
        const val K_GUEST = "profileGuest"
        const val K_ASKED = "consentAsked"
        const val K_MEMO = "memoOn"
        const val K_SUG_ON = "suggestOn"
        const val K_SUG_DAYS = "suggestDays"
        const val K_SUG_SLOTS = "suggestSlots"
    }
}

enum class TimeSlot(val label: String, val fromHour: Int, val toHour: Int) {
    MORNING("오전 9~12시", 9, 12),
    DAY("낮 12~오후 6시", 12, 18),
    EVENING("저녁 6~9시", 18, 21),
}

data class SuggestSettings(val on: Boolean, val days: Set<DayOfWeek>, val slots: Set<TimeSlot>) {
    /**
     * 지금 제안해도 되는지. 시간대를 모두 고르면 "언제든"으로 본다
     * (밤에 시동을 걸어도 카드가 사라지지 않게).
     */
    fun matches(now: LocalDateTime): Boolean {
        if (!on || now.dayOfWeek !in days) return false
        if (slots.size == TimeSlot.entries.size) return true
        return slots.any { now.hour in it.fromHour until it.toHour }
    }
}

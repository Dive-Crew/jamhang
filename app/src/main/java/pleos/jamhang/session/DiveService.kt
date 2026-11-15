package pleos.jamhang.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pleos.jamhang.JamhangApp
import pleos.jamhang.MainActivity
import pleos.jamhang.R
import pleos.jamhang.data.EndType
import pleos.jamhang.vehicle.GearState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 잠항 중에만 도는 Foreground Service (mediaPlayback).
 * - 1초 tick: 종료 시각·1분 전 예고
 * - 차량: P 이탈 → 자동 부상, 운전석 문 열림 → 잠시 멈춤
 * - 오디오: 바다 소리·포커스
 * 화면이 꺼지거나 다른 앱이 앞에 와도 계속된다.
 */
class DiveService : LifecycleService() {

    private lateinit var sound: SoundEngine

    override fun onCreate() {
        super.onCreate()
        val c = (application as JamhangApp).container
        val session = c.session

        sound = c.sound
        sound.focusListener = object : SoundEngine.FocusListener {
            override fun onTransientLoss() = session.pause(PauseReason.AUDIO)
            override fun onTransientRegain() = session.pauseCauseCleared(PauseReason.AUDIO)
            override fun onPermanentLoss() = session.setSoundOn(false)
        }
        startInForeground()

        lifecycleScope.launch {
            while (true) {
                session.tick()
                val s = session.active
                val now = System.currentTimeMillis()
                if (s != null && s.isWarning(now) && s.warnedForEndAt != s.endAt) {
                    session.markWarned()
                    // 19: 알림음 + 안내 음성 ("일 분 뒤에 마쳐요").
                    // 정책(Figma "합의 필요" 8): 소리를 끄면 예고음도 내지 않는다 — 화면 예고(05)만 보인다
                    if (s.soundOn) {
                        sound.play(SoundEngine.Sfx.ONE_MINUTE)
                        sound.playLater(SoundEngine.Sfx.VO_ONE_MINUTE, 1_200)
                    }
                }
                delay(1_000)
            }
        }

        lifecycleScope.launch {
            var first = true
            var wasPaused = false
            session.state.collect { st ->
                when (st) {
                    is SessionState.Active -> {
                        val s = st.session
                        // 03 → 04 넘어갈 때 1회 (복원된 세션은 제외)
                        if (first && System.currentTimeMillis() - s.startedAt < 10_000) sound.play(SoundEngine.Sfx.DIVE_START)
                        // 16 잠시 멈춤 / 이어서 쉬기. 오디오 포커스를 잃어 멈춘 경우엔 소리를 내지 않는다
                        if (!first && s.isPaused != wasPaused) {
                            if (s.soundOn && s.isPaused && s.pauseReason != PauseReason.AUDIO) sound.play(SoundEngine.Sfx.PAUSE)
                            if (s.soundOn && !s.isPaused) sound.play(SoundEngine.Sfx.RESUME)
                        }
                        first = false
                        wasPaused = s.isPaused
                        sound.syncDive(
                            playing = !s.isPaused, soundOn = s.soundOn, volume = s.volume, cabin = s.cabin,
                            // 숨소리는 64초 반복(4초 박자) 대신 BreathScript 박자(5초)에 맞춰 한 번씩 낸다
                            breathOn = false, breathStartedAt = s.breathStartedAt,
                        )
                    }
                    else -> {
                        sound.endDive()
                        stopSelf()
                    }
                }
            }
        }

        // 호흡 안내 (BreathScript, 약 1분): 들숨·날숨이 시작할 때마다 숨소리 한 번 + 안내 음성을 겹쳐 낸다.
        // 화면의 원·문구와 같은 기준 시각(breathStartedAt)에서 계산하므로 셋이 같은 순간에 바뀐다.
        // 안내가 끝나면(B04 완료 2.5초 뒤) 안내를 끄고 조용한 휴식으로 돌아간다. 소리가 꺼져 있어도 끝은 같다.
        lifecycleScope.launch {
            session.state
                .map { st ->
                    val s = (st as? SessionState.Active)?.session
                    s?.takeIf { it.breathGuide && !it.isPaused }?.let { it.breathStartedAt to it.soundOn }
                }
                .distinctUntilChanged()
                .collectLatest { key ->
                    val (startedAt, soundOn) = key ?: return@collectLatest
                    val elapsed = System.currentTimeMillis() - startedAt
                    if (soundOn) {
                        for ((i, at) in BreathScript.boundaries.withIndex()) {
                            if (at < elapsed - BREATH_VOICE_GRACE_MS) continue
                            delay(startedAt + at - System.currentTimeMillis())
                            playBreathCue(session, inhale = i % 2 == 0)
                        }
                    }
                    delay(startedAt + BreathScript.TOTAL_MS - System.currentTimeMillis())
                    if (session.active?.breathStartedAt == startedAt) session.setBreathGuide(false)
                }
        }

        // P 이탈 즉시 자동 부상 (기어를 읽을 수 있을 때만)
        lifecycleScope.launch {
            c.vehicle.state.map { it.gearReadable to it.gear }.distinctUntilChanged().collect { (readable, gear) ->
                if (readable && gear != GearState.PARK && gear != GearState.UNKNOWN) session.finish(EndType.AUTO)
            }
        }
        // 기어를 못 읽는 차량: 주행 규제 진입을 이탈 신호로 사용 (§2 대안)
        lifecycleScope.launch {
            combine(c.vehicle.state, c.restriction.restricted) { v, r -> !v.gearReadable && r }
                .distinctUntilChanged()
                .collect { if (it) session.finish(EndType.AUTO) }
        }
        // 문 열림 → 잠시 멈춤, 닫힘 → "이어서 쉴까요?"
        lifecycleScope.launch {
            c.vehicle.state.map { it.driverDoorOpen }.distinctUntilChanged().collect { open ->
                when (open) {
                    true -> session.pause(PauseReason.DOOR)
                    false -> session.pauseCauseCleared(PauseReason.DOOR)
                    null -> Unit
                }
            }
        }
    }

    /** 들숨/날숨 시작: 숨소리(4초) + "천천히 들이쉬어요/내쉬어요". 1분 전 예고 중에는 그 안내와 겹치지 않게 쉰다 */
    private fun playBreathCue(session: DiveSessionController, inhale: Boolean) {
        val s = session.active ?: return
        if (s.isWarning(System.currentTimeMillis())) return
        sound.play(if (inhale) SoundEngine.Sfx.BREATH_IN else SoundEngine.Sfx.BREATH_OUT)
        sound.play(if (inhale) SoundEngine.Sfx.VO_BREATH_IN else SoundEngine.Sfx.VO_BREATH_OUT)
    }

    override fun onDestroy() {
        sound.focusListener = null
        if ((application as JamhangApp).container.session.active == null) sound.endDive()
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val end = (application as JamhangApp).container.session.active?.endAt
        val text = end?.let { "${formatClock(it)}에 마쳐요" } ?: "조용히 쉬는 중"
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("잠항 중")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    companion object {
        private const val CHANNEL = "dive"
        /** 박자가 시작되고 이 시간 안에 안내가 켜지면 그 박자의 문구를 바로 낸다 */
        private const val BREATH_VOICE_GRACE_MS = 600L
        private const val NOTIFICATION_ID = 7

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DiveService::class.java))
        }

        fun formatClock(epochMs: Long): String =
            Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN))
    }
}

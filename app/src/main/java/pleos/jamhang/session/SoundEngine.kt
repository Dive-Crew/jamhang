package pleos.jamhang.session

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.annotation.RawRes
import pleos.jamhang.R
import pleos.jamhang.data.Cabin

/**
 * 잠항 사운드 v2 (docs/sound/README.md) 재생기. 앱에 하나 (AppContainer 소유).
 *
 * - 배경음(선실별 64초 반복)과 숨소리(호흡 안내, 64초 = 8주기 반복)는 MediaPlayer
 * - 효과음·안내 음성은 SoundPool
 * - 권장 볼륨(슬라이더 100% 기준): 배경음 0.8 · 숨소리 1.0 · 음성 1.0 · 효과음 0.8
 *
 * 오디오 포커스 정책 (정책 보드 3):
 * - CAN_DUCK: 볼륨 0.3배, 휴식 계속
 * - LOSS_TRANSIENT: 전부 멈춤 → "잠시 멈췄어요"(15), 포커스가 돌아와도 자동 재개 X → "이어서 쉬기"(16)
 * - LOSS: 바다 소리 끔, 휴식 계속
 */
class SoundEngine(context: Context) {

    enum class Sfx(@param:RawRes val res: Int, val gain: Float) {
        HATCH_CLOSE(R.raw.sfx_hatch_close, 0.8f),
        DIVE_START(R.raw.sfx_dive_start, 0.8f),
        ONE_MINUTE(R.raw.sfx_notice_one_minute, 0.8f),
        VO_ONE_MINUTE(R.raw.vo_f_one_minute, 1.0f),
        PAUSE(R.raw.sfx_pause, 0.8f),
        RESUME(R.raw.sfx_resume, 0.8f),
        SURFACE_COMPLETE(R.raw.sfx_surface_complete, 0.8f),
        LAYER_ADDED(R.raw.sfx_layer_added, 0.8f),
        /** 호흡 안내: 들숨 시작에 "천천히 들이쉬어요" — 숨소리 위에 겹쳐 재생 */
        VO_BREATH_IN(R.raw.vo_f_breath_in, 1.0f),
        /** 호흡 안내: 날숨 시작에 "천천히 내쉬어요" */
        VO_BREATH_OUT(R.raw.vo_f_breath_out, 1.0f),
        /** 호흡 안내: 들숨·날숨 숨소리 한 번 (4초, 5초 박자에 맞춰 1초 쉼) */
        BREATH_IN(R.raw.breath_human_in_4s, 1.0f),
        BREATH_OUT(R.raw.breath_human_out_4s, 1.0f),
    }

    /** DiveService가 연결. 포커스 변화를 세션 상태로 바꾼다 */
    interface FocusListener {
        fun onTransientLoss()
        fun onTransientRegain()
        fun onPermanentLoss()
    }

    var focusListener: FocusListener? = null

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
    private val soundIds = mutableMapOf<Sfx, Int>()
    private val loaded = mutableSetOf<Int>()

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id }
        Sfx.entries.forEach { soundIds[it] = pool.load(appContext, it.res, 1) }
    }

    // ---- 상태 ----
    private var bed: MediaPlayer? = null
    private var bedCabin: Cabin? = null
    private var breath: MediaPlayer? = null
    private var hasFocus = false
    private var ducked = false
    private var master = 0.6f
    /** 마지막으로 본 소리 켜짐 상태 — 세션이 끝난 뒤(결과 화면) 효과음에도 적용 */
    var soundOn = true
        private set

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attrs)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(::onFocusChange, main)
        .build()

    /**
     * DiveService가 세션 상태가 바뀔 때마다 호출.
     * @param breathStartedAt 호흡 안내 박자의 기준 시각. 화면의 점 애니메이션도 같은 값을 써서 박자가 맞는다
     */
    fun syncDive(
        playing: Boolean, soundOn: Boolean, volume: Float, cabin: Cabin,
        breathOn: Boolean, breathStartedAt: Long,
    ) {
        this.soundOn = soundOn
        master = volume
        val want = playing && soundOn
        if (want && !hasFocus) {
            hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (!want || !hasFocus) {
            bed?.takeIf { it.isPlaying }?.pause()
            breath?.takeIf { it.isPlaying }?.pause()
            return
        }
        // 배경음: 선실이 바뀌면 새 파일로
        if (bedCabin != cabin) {
            bed?.release()
            bed = loop(cabin.bedRes())
            bedCabin = cabin
        }
        bed?.let { if (!it.isPlaying) it.start() }

        // 숨소리: 박자 기준 시각에 맞춰 위치를 잡고 재생
        if (breathOn) {
            val b = breath ?: loop(R.raw.breath_human_64s_loop).also { breath = it }
            if (b != null && !b.isPlaying) {
                val pos = ((System.currentTimeMillis() - breathStartedAt).coerceAtLeast(0) % BREATH_LOOP_MS).toInt()
                b.seekTo(pos)
                b.start()
            }
        } else {
            breath?.let { fadeOutAndRelease(it, 500) }
            breath = null
        }
        applyVolume()
    }

    /** 결과 화면으로 넘어갈 때: 배경음·숨소리를 1초 동안 줄이며 정지 */
    fun endDive() {
        bed?.let { fadeOutAndRelease(it, 1000) }
        breath?.let { fadeOutAndRelease(it, 1000) }
        bed = null; bedCabin = null; breath = null
        if (hasFocus) audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    /** @return 정지에 쓸 stream id (0 = 재생 안 함) */
    fun play(sfx: Sfx, respectMute: Boolean = true): Int {
        if (respectMute && !soundOn) return 0
        val id = soundIds[sfx] ?: return 0
        if (id !in loaded) return 0
        val v = sfx.gain * (if (ducked) 0.3f else 1f)
        return pool.play(id, v, v, 1, 0, 1f)
    }

    fun playLater(sfx: Sfx, delayMs: Long) {
        main.postDelayed({ play(sfx) }, delayMs)
    }

    fun stop(streamId: Int) {
        if (streamId != 0) pool.stop(streamId)
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                val wasLost = !hasFocus
                hasFocus = true
                ducked = false
                applyVolume()
                if (wasLost) focusListener?.onTransientRegain()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true
                applyVolume()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasFocus = false
                bed?.pause(); breath?.pause()
                focusListener?.onTransientLoss()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                bed?.pause(); breath?.pause()
                audioManager.abandonAudioFocusRequest(focusRequest)
                focusListener?.onPermanentLoss()
            }
        }
    }

    private fun loop(@RawRes res: Int): MediaPlayer? =
        MediaPlayer.create(appContext, res, attrs, audioManager.generateAudioSessionId())?.apply { isLooping = true }

    private fun applyVolume() {
        val duck = if (ducked) 0.3f else 1f
        val bv = BED_GAIN * master * duck
        val rv = BREATH_GAIN * master * duck
        bed?.setVolume(bv, bv)
        breath?.setVolume(rv, rv)
    }

    private fun fadeOutAndRelease(p: MediaPlayer, ms: Long) {
        val steps = 10
        val start = if (p === breath) BREATH_GAIN * master else BED_GAIN * master
        for (i in 1..steps) {
            main.postDelayed({
                runCatching {
                    val v = start * (1f - i / steps.toFloat())
                    p.setVolume(v, v)
                    if (i == steps) { p.stop(); p.release() }
                }
            }, ms * i / steps)
        }
    }

    private fun Cabin.bedRes(): Int = when (this) {
        Cabin.LIGHT_SEA -> R.raw.amb_light_sea
        Cabin.SEAWEED -> R.raw.amb_kelp_forest
        Cabin.OLD_GATE -> R.raw.amb_old_gate
        Cabin.PALACE -> R.raw.amb_dragon_palace
    }

    companion object {
        const val BED_GAIN = 0.8f
        const val BREATH_GAIN = 1.0f
        /** breath_human_64s_loop = 들숨 4초 · 날숨 4초 × 8 */
        const val BREATH_LOOP_MS = 64_000L
        const val BREATH_HALF_MS = 4_000L
    }
}

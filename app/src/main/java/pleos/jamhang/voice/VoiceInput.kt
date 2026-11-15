package pleos.jamhang.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 한 줄 메모 음성 입력 (Pleos SpeechToText SDK, STT_SERVICE).
 * 원칙: 음성은 앱에 저장하지 않는다. 녹음·전송은 Pleos 음성 서비스가 하고, 앱은 글자로 바뀐 결과만 받는다.
 * 서비스가 없거나 초기화에 실패하면 [available]=false → 마이크 버튼을 숨기고 키보드만 쓴다.
 */
class VoiceInput(context: Context) {

    sealed interface State {
        data object Idle : State
        data class Listening(val partial: String) : State
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var stt: SpeechToText? = null
    private var ready = false
    private var onFinal: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available.asStateFlow()
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val listener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) = main.post {
            if (_state.value !is State.Listening) return@post
            if (isFinal) {
                _state.value = State.Idle
                text.trim().takeIf { it.isNotEmpty() }?.let { onFinal?.invoke(it) }
            } else {
                _state.value = State.Listening(text)
            }
        }.let { }

        override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) {}
        override fun onStartedRecognition() {}
        override fun onEndedRecognition() = main.post {
            // 끝까지 최종 결과가 없으면 마지막 중간 결과를 쓴다
            val s = _state.value
            if (s is State.Listening) {
                _state.value = State.Idle
                s.partial.trim().takeIf { it.isNotEmpty() }?.let { onFinal?.invoke(it) }
            }
        }.let { }

        override fun onError(error: ErrorType) = main.post {
            Log.w(TAG, "stt error $error")
            if (_state.value is State.Listening) {
                _state.value = State.Idle
                onError?.invoke(
                    when (error) {
                        ErrorType.MIC_ERROR, ErrorType.AUDIO_ERROR -> "마이크를 쓸 수 없어요"
                        ErrorType.NETWORK_ERROR, ErrorType.TIMEOUT -> "잘 들리지 않았어요. 다시 말해 주세요"
                        else -> "음성으로 남기지 못했어요. 키보드로 적어 주세요"
                    },
                )
            }
        }.let { }

        override fun onReady() {}
        override fun onUpdatedRms(rms: Double) {}
    }

    /** 듣기 시작. 첫 호출 때 SDK를 초기화한다 */
    fun start(onText: (String) -> Unit, onFail: (String) -> Unit) {
        onFinal = onText
        onError = onFail
        _state.value = State.Listening("")
        val s = stt
        if (s != null && ready) {
            runCatching { s.request() }.onFailure { fail(it) }
            return
        }
        if (s != null) return // 초기화 중
        runCatching {
            SpeechToText(appContext, Mode.ON_DEVICE).also { created ->
                stt = created
                created.initialize(object : OnInitializeListener {
                    override fun onSuccess() = main.post {
                        ready = true
                        runCatching {
                            created.setLocale(Locale.KOREAN)
                            created.addListener(listener)
                            if (_state.value is State.Listening) created.request()
                        }.onFailure { fail(it) }
                    }.let { }

                    override fun onFailure(error: ErrorType) = main.post {
                        Log.w(TAG, "stt init failed $error")
                        stt = null
                        _available.value = false
                        if (_state.value is State.Listening) {
                            _state.value = State.Idle
                            onError?.invoke("이 차에서는 음성 입력을 쓸 수 없어요")
                        }
                    }.let { }
                })
            }
        }.onFailure { fail(it); _available.value = false }
    }

    fun stop() {
        if (_state.value !is State.Listening) return
        runCatching { stt?.stop() }
    }

    /** 화면을 떠날 때: 듣기를 멈추고 결과를 버린다 */
    fun cancel() {
        onFinal = null
        onError = null
        runCatching { stt?.stop() }
        _state.value = State.Idle
    }

    private fun fail(t: Throwable) {
        Log.w(TAG, "stt failed", t)
        stt = null
        ready = false
        _state.value = State.Idle
        onError?.invoke("음성으로 남기지 못했어요. 키보드로 적어 주세요")
    }

    private companion object {
        const val TAG = "JamhangVoice"
    }
}

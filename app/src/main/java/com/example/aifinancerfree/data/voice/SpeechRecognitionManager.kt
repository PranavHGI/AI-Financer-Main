package com.example.aifinancerfree.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechRecognitionManager(private val context: Context) {

    companion object {
        private const val TAG = "SpeechRecognizer"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(locale: Locale = Locale.getDefault()) {
        mainHandler.post {
            try {
                cancel()

                if (!isRecognitionAvailable) {
                    _state.value = SpeechState.Error("Speech recognition is not supported on this device.")
                    return@post
                }

                _state.value = SpeechState.Initializing
                _partialTranscript.value = ""

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start speech listening", e)
                _state.value = SpeechState.Error("Failed to initialize speech input: ${e.localizedMessage}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                _state.value = SpeechState.Processing
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping listener", e)
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling recognizer", e)
            } finally {
                _state.value = SpeechState.Idle
                _partialTranscript.value = ""
            }
        }
    }

    fun destroy() {
        cancel()
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "SpeechRecognizer is ready")
                _state.value = SpeechState.Ready
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech begun")
                _state.value = SpeechState.Listening(0f)
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (_state.value is SpeechState.Listening) {
                    _state.value = SpeechState.Listening(rmsdB.coerceAtLeast(0f))
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "Speech ended, processing...")
                _state.value = SpeechState.Processing
            }

            override fun onError(error: Int) {
                val errorMsg = mapErrorCode(error)
                Log.w(TAG, "SpeechRecognizer error: $errorMsg ($error)")
                _state.value = SpeechState.Error(errorMsg, error)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull() ?: ""
                Log.i(TAG, "Final Speech Result: '$recognizedText'")
                _partialTranscript.value = recognizedText
                _state.value = SpeechState.Result(recognizedText)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val liveText = partials?.firstOrNull() ?: ""
                if (liveText.isNotBlank()) {
                    _partialTranscript.value = liveText
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun mapErrorCode(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Client error. Recognition cancelled."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout while contacting speech service."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech was heard or matched. Please try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service is currently busy."
            SpeechRecognizer.ERROR_SERVER -> "Recognition server error. Please retry."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Timed out."
            else -> "Speech recognition failed (Code: $error)."
        }
    }
}

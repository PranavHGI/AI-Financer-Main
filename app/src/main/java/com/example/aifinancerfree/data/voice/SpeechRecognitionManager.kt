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
    private var activeListener: RecognitionListener? = null

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun destroyRecognizerInternal() {
        activeListener = null
        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        } finally {
            speechRecognizer = null
        }
    }

    fun startListening(locale: Locale = Locale.getDefault()) {
        runOnMainThread {
            try {
                // Synchronously tear down any previous instance to avoid asynchronous self-cancellation
                destroyRecognizerInternal()

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.e(TAG, "Speech recognition is not supported on this device.")
                    _state.value = SpeechState.Error("Speech recognition is not supported or enabled on this device.")
                    return@runOnMainThread
                }

                _state.value = SpeechState.Initializing
                _partialTranscript.value = ""

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer
                val listener = createListener()
                activeListener = listener
                recognizer.setRecognitionListener(listener)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                recognizer.startListening(intent)
                Log.d(TAG, "Started speech recognition successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start speech listening", e)
                _state.value = SpeechState.Error("Failed to initialize speech input: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun stopListening() {
        runOnMainThread {
            try {
                speechRecognizer?.stopListening()
                _state.value = SpeechState.Processing
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping listener", e)
            }
        }
    }

    fun cancel() {
        runOnMainThread {
            destroyRecognizerInternal()
            _state.value = SpeechState.Idle
            _partialTranscript.value = ""
        }
    }

    fun destroy() {
        cancel()
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (this !== activeListener) return
                Log.d(TAG, "SpeechRecognizer is ready for speech")
                _state.value = SpeechState.Ready
            }

            override fun onBeginningOfSpeech() {
                if (this !== activeListener) return
                Log.d(TAG, "Speech began")
                _state.value = SpeechState.Listening(0f)
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (this !== activeListener) return
                val currentState = _state.value
                if (currentState is SpeechState.Listening || currentState is SpeechState.Ready) {
                    _state.value = SpeechState.Listening(rmsdB.coerceAtLeast(0f))
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (this !== activeListener) return
                Log.d(TAG, "Speech ended, processing...")
                _state.value = SpeechState.Processing
            }

            override fun onError(error: Int) {
                if (this !== activeListener) return

                // If the error was client cancellation or disconnection from teardown, ignore gracefully
                if (error == SpeechRecognizer.ERROR_CLIENT || error == 11) {
                    Log.d(TAG, "SpeechRecognizer client cancelled / disconnected (code $error)")
                    return
                }

                val errorMsg = mapErrorCode(error)
                Log.w(TAG, "SpeechRecognizer error: $errorMsg ($error)")
                _state.value = SpeechState.Error(errorMsg, error)
            }

            override fun onResults(results: Bundle?) {
                if (this !== activeListener) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()?.trim() ?: ""
                Log.i(TAG, "Final Speech Result: '$recognizedText'")
                if (recognizedText.isNotBlank()) {
                    _partialTranscript.value = recognizedText
                    _state.value = SpeechState.Result(recognizedText)
                } else {
                    _state.value = SpeechState.Error("No speech detected. Tap mic to speak.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (this !== activeListener) return
                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val liveText = partials?.firstOrNull()?.trim() ?: ""
                if (liveText.isNotBlank()) {
                    Log.d(TAG, "Partial speech: '$liveText'")
                    _partialTranscript.value = liveText
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun mapErrorCode(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition cancelled."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition. Tap mic to retry."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout. Tap mic to retry."
            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap mic to speak."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy. Please try again."
            SpeechRecognizer.ERROR_SERVER -> "Recognition server error. Tap mic to retry."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap mic to speak."
            10 -> "Recognition service busy. Please try again."
            11 -> "Recognition service disconnected. Tap mic to retry."
            12 -> "Speech model download required."
            13 -> "Language not supported."
            14 -> "Language model not available."
            else -> "Speech recognition issue (Code: $error). Tap mic to retry."
        }
    }
}

package com.example.aifinancerfree.data.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TextToSpeechManager"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val callbackMap = mutableMapOf<String, Pair<(() -> Unit)?, (() -> Unit)?>>()

    fun initialize() {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ttsEngine = tts ?: return
            
            // Try Indian English first, then device locale, then US English
            val targetLocale = Locale("en", "IN")
            val result = ttsEngine.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                val fallbackResult = ttsEngine.setLanguage(Locale.getDefault())
                if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsEngine.setLanguage(Locale.US)
                }
            }

            // Tune natural pitch and speech rate
            ttsEngine.setPitch(1.0f)
            ttsEngine.setSpeechRate(1.0f)

            ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    utteranceId?.let { id ->
                        callbackMap[id]?.first?.invoke()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    utteranceId?.let { id ->
                        callbackMap.remove(id)?.second?.invoke()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    utteranceId?.let { id ->
                        callbackMap.remove(id)
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "TTS playback error for ID: $utteranceId with code: $errorCode")
                    _isSpeaking.value = false
                    utteranceId?.let { id ->
                        callbackMap.remove(id)
                    }
                }
            })

            isInitialized = true
            Log.i(TAG, "TextToSpeech initialized successfully with locale: ${ttsEngine.voice?.locale ?: ttsEngine.language}")
        } else {
            Log.e(TAG, "Failed to initialize TextToSpeech engine. Status: $status")
            isInitialized = false
        }
    }

    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null
    ) {
        if (_isMuted.value) {
            Log.d(TAG, "Speech muted by user preference. Skipping TTS speak.")
            onDone?.invoke()
            return
        }

        if (tts == null) {
            initialize()
        }

        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS engine not ready yet. Skipping speech.")
            onDone?.invoke()
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        callbackMap[utteranceId] = Pair(onStart, onDone)

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts?.speak(text, queueMode, params, utteranceId)
    }

    fun stop() {
        try {
            tts?.stop()
            _isSpeaking.value = false
            callbackMap.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS playback", e)
        }
    }

    fun toggleMute(): Boolean {
        val newMute = !_isMuted.value
        _isMuted.value = newMute
        if (newMute) {
            stop()
        }
        return newMute
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        if (muted) {
            stop()
        }
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TextToSpeech", e)
        }
    }
}

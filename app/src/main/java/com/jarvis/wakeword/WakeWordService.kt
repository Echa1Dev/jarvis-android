package com.jarvis.wakeword

import android.app.*
import android.content.Intent
import android.media.MediaRecorder
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class WakeWordService : Service() {

    private val CHANNEL_ID = "jarvis_channel"
    private val TAG = "WakeWordService"
    private val BOT_TOKEN = BuildConfig.BOT_TOKEN
    private val CHAT_ID = BuildConfig.CHAT_ID

    private var speechRecognizer: SpeechRecognizer? = null
    private val isCapturing = AtomicBoolean(false)
    private var mediaRecorder: MediaRecorder? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification("Jarvis escuchando..."))
        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        handler.post {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.lowercase() ?: ""
                    Log.d(TAG, "Heard: $text")
                    if (text.contains("jarvis") && !isCapturing.get()) {
                        triggerCapture()
                    } else {
                        scheduleRestart(300)
                    }
                }
                override fun onError(error: Int) { scheduleRestart(800) }
                override fun onEndOfSpeech() {}
                override fun onReadyForSpeech(p: Bundle) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray) {}
                override fun onPartialResults(p: Bundle) {}
                override fun onEvent(t: Int, p: Bundle) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (isCapturing.get()) return
        handler.postDelayed({ startListening() }, delayMs)
    }

    private fun triggerCapture() {
        isCapturing.set(true)
        updateNotification("Habla...")
        speechRecognizer?.stopListening()

        val outFile = File(cacheDir, "cmd_${System.currentTimeMillis()}.mp4")

        try {
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recorder error", e)
            resetToListening()
            return
        }

        handler.postDelayed({
            stopAndSend(outFile)
        }, 7000)
    }

    private fun stopAndSend(file: File) {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null

        if (file.exists() && file.length() > 1024) {
            updateNotification("Enviando...")
            Thread { sendVoice(file) }.start()
        } else {
            file.delete()
            resetToListening()
        }
    }

    private fun sendVoice(file: File) {
        try {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", CHAT_ID)
                .addFormDataPart("voice", file.name, file.asRequestBody("audio/mp4".toMediaType()))
                .build()
            val req = Request.Builder()
                .url("https://api.telegram.org/bot$BOT_TOKEN/sendVoice")
                .post(body).build()
            OkHttpClient().newCall(req).execute().use { res ->
                Log.d(TAG, "Telegram: ${res.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send error", e)
        } finally {
            file.delete()
            resetToListening()
        }
    }

    private fun resetToListening() {
        isCapturing.set(false)
        updateNotification("Jarvis escuchando...")
        handler.postDelayed({ startListening() }, 500)
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Jarvis", NotificationManager.IMPORTANCE_LOW)
            ch.setSound(null, null)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        speechRecognizer?.destroy()
        mediaRecorder?.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

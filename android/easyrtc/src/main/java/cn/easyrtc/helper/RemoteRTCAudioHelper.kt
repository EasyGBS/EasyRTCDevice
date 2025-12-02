package cn.easyrtc.helper

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioTrack.getMaxVolume
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class RemoteRTCAudioHelper(private val context: Context) {

    private var audioTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null

    // 队列存储 ByteArray，直接使用传入的数据
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackThread: Thread? = null
    private var isPlaying = false
    private var shouldStop = false
    private var lastDataTime = System.currentTimeMillis()

    companion object {
        private const val TAG = "RemoteRTCAudioHelper"
        private const val SAMPLE_RATE = 8000
        private const val PLAYBACK_BUFFER_SIZE = 4096
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_QUEUE_SIZE = 50
        private const val AUTO_STOP_TIMEOUT = 5000L
    }

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /**
     * 接收远端音频帧并播放 - 直接使用原始 ByteArray
     * 注意：调用方不应该在调用此方法后修改 data 数组
     */
    fun processRemoteAudioFrame(data: ByteArray, size: Int) {
        // 直接使用原始数据，零拷贝
        addToPlaybackQueue(data, size)
//        MagicFileHelper.getInstance().saveToFile(data, "remote.pcm")
    }

    /**
     * 将音频数据添加到播放队列 - 零拷贝版本
     */
    private fun addToPlaybackQueue(audioData: ByteArray, size: Int) {
        if (!isPlaying) {
            startAudioPlayback()
        }

        try {
            // 直接使用原始数组，假设调用方不会修改
            if (audioQueue.size > MAX_QUEUE_SIZE) {
                audioQueue.poll()
                Log.w(TAG, "Audio queue full, dropping old data")
            }
            audioQueue.put(audioData)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Failed to add audio data to queue: ${e.message}")
        }
    }

    /**
     * 如果需要线程安全的版本（当调用方可能修改数据时）
     */
    fun processRemoteAudioFrameSafe(data: ByteArray, size: Int) {
        // 创建副本确保线程安全
        val safeCopy = data.copyOf(size)
        addToPlaybackQueue(safeCopy, size)
    }

    // 其他方法保持不变...
    private fun startAudioPlayback() {
        if (isPlaying) return

        try {
            setupAudioForSpeaker()
            // {USAGE_VOICE_COMMUNICATION | CONTENT_TYPE_SPEECH}  {USAGE_MEDIA |CONTENT_TYPE_MUSIC}
            audioTrack = AudioTrack.Builder().setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            ).setAudioFormat(
                AudioFormat.Builder().setEncoding(AUDIO_FORMAT).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            ).setBufferSizeInBytes(PLAYBACK_BUFFER_SIZE).build()

            audioTrack?.apply {
                setVolume(getMaxVolume() / 2)
                play()
            }

            startPlaybackThread()
            isPlaying = true
            shouldStop = false
            Log.d(TAG, "Audio playback started")
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback start error: ${e.message}")
        }
    }

    private fun startPlaybackThread() {
        playbackThread = Thread {
            Log.d(TAG, "Audio playback thread started")

            while (!shouldStop) {
                try {
                    val audioData = audioQueue.poll(100, TimeUnit.MILLISECONDS)
                    if (audioData != null) {
                        writeAudioData(audioData)
                    } else {
                        if (audioQueue.isEmpty() && System.currentTimeMillis() - lastDataTime > AUTO_STOP_TIMEOUT) {
                            Log.d(TAG, "No audio data for $AUTO_STOP_TIMEOUT ms, auto stopping")
                            release()
                            break
                        }
                    }
                } catch (e: InterruptedException) {
                    Log.d(TAG, "Audio playback thread interrupted")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Audio playback thread error: ${e.message}")
                }
            }

            Log.d(TAG, "Audio playback thread stopped")
        }.apply {
            name = "AudioPlaybackThread"
            start()
        }
    }

    private fun writeAudioData(audioData: ByteArray) {
        audioTrack?.let { track ->
            try {
                lastDataTime = System.currentTimeMillis()

                var remaining = audioData.size
                var offset = 0

                while (remaining > 0 && !shouldStop) {
                    val written = track.write(audioData, offset, remaining)
                    if (written > 0) {
                        remaining -= written
                        offset += written
                    } else {
                        Log.e(TAG, "AudioTrack write error: $written")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio playback write error: ${e.message}")
            }
        }
    }

    private fun setupAudioForSpeaker() {
        audioManager?.apply {
            try {
                isSpeakerphoneOn = true
                val maxMusicVolume = getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVolume, 0)
                isBluetoothScoOn = false
                stopBluetoothSco()
            } catch (e: Exception) {
                Log.e(TAG, "Audio configuration error: ${e.message}")
            }
        }
    }

    fun release() {
        shouldStop = true
        isPlaying = false
        playbackThread?.interrupt()
        audioQueue.clear()

        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback release error: ${e.message}")
        }
    }

    fun isPlaying(): Boolean = isPlaying
    fun getQueueSize(): Int = audioQueue.size
}
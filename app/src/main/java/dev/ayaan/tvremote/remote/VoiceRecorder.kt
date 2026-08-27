package dev.ayaan.tvremote.remote

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Captures the phone's mic as 16-bit PCM / 8 kHz / mono -- exactly the
 * format the Android TV Remote v2 voice channel expects, so no encoding or
 * resampling is needed between capture and the wire.
 */
class VoiceRecorder(private val scope: CoroutineScope) {

    private var job: Job? = null

    /**
     * Set the moment `AudioRecord` is created, cleared once the capture loop's
     * `finally` releases it. `stop()` calls `.stop()` on this directly, from
     * whatever thread the caller is on -- cancelling [job] alone only makes
     * the loop exit *after* its current blocking `read()` returns, and that
     * read has no reason to return on its own if nothing calls `.stop()` on
     * the record itself. Android documents exactly this: stopping an active
     * `AudioRecord` unblocks a `read()` in progress on another thread.
     */
    @Volatile private var record: AudioRecord? = null

    @SuppressLint("MissingPermission") // caller has already checked RECORD_AUDIO
    fun start(onChunk: (ByteArray) -> Unit) {
        if (job?.isActive == true) return

        job = scope.launch(Dispatchers.IO) {
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
            if (minBuffer <= 0) return@launch

            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                minBuffer * 2
            )
            record = rec
            val buffer = ByteArray(READ_SIZE)
            val pending = java.io.ByteArrayOutputStream(CHUNK_SIZE * 2)

            try {
                rec.startRecording()
                while (isActive) {
                    val read = rec.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    pending.write(buffer, 0, read)
                    if (pending.size() >= CHUNK_SIZE) {
                        onChunk(pending.toByteArray())
                        pending.reset()
                    }
                }
                if (pending.size() > 0) onChunk(pending.toByteArray())
            } finally {
                runCatching { rec.stop() }
                rec.release()
                record = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { record?.stop() }
    }

    private companion object {
        const val SAMPLE_RATE = 8000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val READ_SIZE = 2048
        const val CHUNK_SIZE = 16 * 1024
    }
}

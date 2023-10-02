package me.tpcreative.audiovisualizer

import android.Manifest
import android.R
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import me.tpcreative.audiovisualizer.databinding.ActivityRecorderBinding
import me.tpcreative.audiovisualizer.visualizer.MyCustomView
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean


class RecorderActivity : AppCompatActivity() {

    private lateinit var binding :ActivityRecorderBinding
    private lateinit var visualizer : MyCustomView

    private val   RECORDER_BPP = 16

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecorderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        visualizer = binding.visualizer

        startButton = binding.btnStart
        startButton!!.setOnClickListener {
            startRecording()
            startButton!!.isEnabled = false
            stopButton!!.isEnabled = true
        }
        stopButton = binding.btnStop
        stopButton!!.setOnClickListener {
            stopRecording()
            startButton!!.isEnabled = true
            stopButton!!.isEnabled = false
        }
    }

    private val SAMPLING_RATE_IN_HZ = 44100

    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    /**
     * Factor by that the minimum buffer size is multiplied. The bigger the factor is the less
     * likely it is that samples will be dropped, but more memory will be used. The minimum buffer
     * size is determined by [AudioRecord.getMinBufferSize] and depends on the
     * recording settings.
     */
    private val BUFFER_SIZE_FACTOR = 2

    /**
     * Size of the buffer where the audio data is stored by Android
     */
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
        SAMPLING_RATE_IN_HZ,
        CHANNEL_CONFIG, AUDIO_FORMAT
    ) * BUFFER_SIZE_FACTOR

    /**
     * Signals whether a recording is in progress (true) or not (false).
     */
    private val recordingInProgress = AtomicBoolean(false)

    private var recorder: AudioRecord? = null

    private var recordingThread: Thread? = null

    private var startButton: Button? = null

    private var stopButton: Button? = null


    override fun onResume() {
        super.onResume()
        startButton!!.isEnabled = true
        stopButton!!.isEnabled = false
    }

    override fun onPause() {
        super.onPause()
        stopRecording()
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }


//        recorder = AudioRecord(
//            MediaRecorder.AudioSource.MIC, freq,
//            CHANNEL_CONFIG, MediaRecorder.AudioEncoder.AMR_NB, BUFFER_SIZE
//        )

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE
        )
        recorder!!.startRecording()
        recordingInProgress.set(true)
        recordingThread = Thread(RecordingRunnable(), "Recording Thread")
        recordingThread!!.start()
        visualizer.onStart?.invoke()
    }


    private fun stopRecording() {
        if (null == recorder) {
            return
        }
        recordingInProgress.set(false)
        recorder!!.stop()
        recorder!!.release()
        visualizer.onStop?.invoke()
        recorder = null
        recordingThread = null
    }


    private inner class RecordingRunnable : Runnable {
        override fun run() {
            var numberOfReadBytes = 0
            val audioBuffer = ByteArray(BUFFER_SIZE)
            var recording = false
            val tempFloatBuffer = FloatArray(3)
            var tempIndex = 0
            var totalReadBytes = 0
            val totalByteBuffer = ByteArray(60 * 44100 * 2)


            // While data come from microphone.
            while (true) {
                var totalAbsValue = 0.0f
                var sample: Short = 0
                if (recorder==null){
                    return
                }
                numberOfReadBytes = recorder!!.read(audioBuffer, 0, BUFFER_SIZE)

                // Analyze Sound.
                run {
                    var i = 0
                    while (i < BUFFER_SIZE) {
                        sample = (audioBuffer[i].toInt() or (audioBuffer[i + 1]
                            .toInt() shl 8)).toShort()
                        totalAbsValue += (Math.abs(sample.toInt()) / (numberOfReadBytes / 2)).toFloat()
                        i += 2
                    }
                }

                // Analyze temp buffer.
                tempFloatBuffer[tempIndex % 3] = totalAbsValue
                var temp = 0.0f
                for (i in 0..2) temp += tempFloatBuffer[i]
                if (temp >= 0 && temp <= 350 && recording == false) {
                    Log.i("TAG", "1")
                    tempIndex++
                    continue
                }
                if (temp > 350 && recording == false) {
                    Log.i("TAG", "2")
                    recording = true
                }
                if (temp >= 0 && temp <= 350 && recording == true) {
                    Log.i("TAG", "Save audio to file.")

                    // Save audio to file.
                    val filepath = Environment.getExternalStorageDirectory().path
                    val file = File(filepath, "AudioRecorder")
                    if (!file.exists()) file.mkdirs()
                    val fn = file.absolutePath + "/" + System.currentTimeMillis() + ".wav"
                    var totalAudioLen: Long = 0
                    var totalDataLen = totalAudioLen + 36
                    val longSampleRate: Long = SAMPLING_RATE_IN_HZ.toLong()
                    val channels = 1
                    val byteRate: Long = RECORDER_BPP * SAMPLING_RATE_IN_HZ.toLong() * channels / 8
                    totalAudioLen = totalReadBytes.toLong()
                    totalDataLen = totalAudioLen + 36
                    val finalBuffer = ByteArray(totalReadBytes + 44)
                    finalBuffer[0] = 'R'.code.toByte() // RIFF/WAVE header
                    finalBuffer[1] = 'I'.code.toByte()
                    finalBuffer[2] = 'F'.code.toByte()
                    finalBuffer[3] = 'F'.code.toByte()
                    finalBuffer[4] = (totalDataLen and 0xffL).toByte()
                    finalBuffer[5] = (totalDataLen shr 8 and 0xffL).toByte()
                    finalBuffer[6] = (totalDataLen shr 16 and 0xffL).toByte()
                    finalBuffer[7] = (totalDataLen shr 24 and 0xffL).toByte()
                    finalBuffer[8] = 'W'.code.toByte()
                    finalBuffer[9] = 'A'.code.toByte()
                    finalBuffer[10] = 'V'.code.toByte()
                    finalBuffer[11] = 'E'.code.toByte()
                    finalBuffer[12] = 'f'.code.toByte() // 'fmt ' chunk
                    finalBuffer[13] = 'm'.code.toByte()
                    finalBuffer[14] = 't'.code.toByte()
                    finalBuffer[15] = ' '.code.toByte()
                    finalBuffer[16] = 16 // 4 bytes: size of 'fmt ' chunk
                    finalBuffer[17] = 0
                    finalBuffer[18] = 0
                    finalBuffer[19] = 0
                    finalBuffer[20] = 1 // format = 1
                    finalBuffer[21] = 0
                    finalBuffer[22] = channels.toByte()
                    finalBuffer[23] = 0
                    finalBuffer[24] = (longSampleRate and 0xffL).toByte()
                    finalBuffer[25] = (longSampleRate shr 8 and 0xffL).toByte()
                    finalBuffer[26] = (longSampleRate shr 16 and 0xffL).toByte()
                    finalBuffer[27] = (longSampleRate shr 24 and 0xffL).toByte()
                    finalBuffer[28] = (byteRate and 0xffL).toByte()
                    finalBuffer[29] = (byteRate shr 8 and 0xffL).toByte()
                    finalBuffer[30] = (byteRate shr 16 and 0xffL).toByte()
                    finalBuffer[31] = (byteRate shr 24 and 0xffL).toByte()
                    finalBuffer[32] = (2 * 16 / 8).toByte() // block align
                    finalBuffer[33] = 0
                    finalBuffer[34] = RECORDER_BPP.toByte() // bits per sample
                    finalBuffer[35] = 0
                    finalBuffer[36] = 'd'.code.toByte()
                    finalBuffer[37] = 'a'.code.toByte()
                    finalBuffer[38] = 't'.code.toByte()
                    finalBuffer[39] = 'a'.code.toByte()
                    finalBuffer[40] = (totalAudioLen and 0xffL).toByte()
                    finalBuffer[41] = (totalAudioLen shr 8 and 0xffL).toByte()
                    finalBuffer[42] = (totalAudioLen shr 16 and 0xffL).toByte()
                    finalBuffer[43] = (totalAudioLen shr 24 and 0xffL).toByte()
                    for (i in 0 until totalReadBytes) finalBuffer[44 + i] = totalByteBuffer[i]
                    val out: FileOutputStream
                    try {
                        out = FileOutputStream(fn)
                        try {
                            out.write(finalBuffer)
                            out.close()
                        } catch (e: IOException) {
                            // TODO Auto-generated catch block
                            e.printStackTrace()
                        }
                    } catch (e1: FileNotFoundException) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace()
                    }

                    //*/
                    tempIndex++
                    break
                }

                // -> Recording sound here.
                Log.i("TAG", "Recording Sound.")
                for (i in 0 until numberOfReadBytes) totalByteBuffer[totalReadBytes + i] =
                    audioBuffer[i]
                totalReadBytes += numberOfReadBytes
                //*/
                tempIndex++
            }
        }

        private fun getBufferReadFailureReason(errorCode: Int): String {
            return when (errorCode) {
                AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
                AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
                AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
                AudioRecord.ERROR -> "ERROR"
                else -> "Unknown ($errorCode)"
            }
        }
    }

}
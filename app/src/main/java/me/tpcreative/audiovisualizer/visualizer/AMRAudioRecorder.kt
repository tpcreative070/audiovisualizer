package me.tpcreative.audiovisualizer.visualizer

import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.util.Log
import me.tpcreative.audiovisualizer.toLogConsole
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Date


/**
 *
 * Android does not support pause and resume when recording amr audio,
 * so we implement it to provide pause and resume funciton.
 *
 * Created by Water Zhang on 11/25/15.
 */
class AMRAudioRecorder(private var fileDirectory: String) {
    private var singleFile = true
    private var recorder: MediaRecorder? = null
    private val files = ArrayList<String>()
    var audioFilePath: String? = null
        private set
    var isRecording = false
        private set

    fun start(): Boolean {
        prepareRecorder()
        try {
            recorder!!.prepare()
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
        if (recorder != null) {
            recorder!!.start()
            isRecording = true
            return true
        }
        return false
    }


    fun getRecorder() : MediaRecorder? {
        return recorder
    }

    fun pause(): Boolean {
        check(!(recorder == null || !isRecording)) { "[AMRAudioRecorder] recorder is not recording!" }
        recorder!!.stop()
        recorder!!.release()
        recorder = null
        isRecording = false
        return true
    }

    fun resume(): Boolean {
        check(!isRecording) { "[AMRAudioRecorder] recorder is recording!" }
        singleFile = false
        newRecorder()
        return start()
    }

    fun stop(): Boolean {
        if (!isRecording) {
            return merge()
        }
        if (recorder == null) {
            return false
        }
        recorder!!.stop()
        recorder!!.release()
        recorder = null
        isRecording = false
        return merge()
    }

    fun clear() {
        if (recorder != null || isRecording) {
            recorder!!.stop()
            recorder!!.release()
            recorder = null
            isRecording = false
        }
        var i = 0
        val len = files.size
        while (i < len) {
            val file = File(files[i])
            file.delete()
            i++
        }
    }

    fun setAudioFile(filePath: String?) {
        audioFilePath = filePath
    }

    private fun merge(): Boolean {

        // If never paused, just return the file
        if (singleFile) {
            moveFile(files[0], audioFilePath)
            return true
        }

        // Merge files
        try {
            val fos = FileOutputStream(audioFilePath)
            var i = 0
            val len = files.size
            while (i < len) {
                val file = File(files[i])
                val fis = FileInputStream(file)

                // Skip file header bytes,
                // amr file header's length is 6 bytes
                if (i > 0) {
                    for (j in 0..5) {
                        fis.read()
                    }
                }
                val buffer = ByteArray(512)
                var count = 0
                while (fis.read(buffer).also { count = it } != -1) {
                    fos.write(buffer, 0, count)
                }
                fis.close()
                fos.flush()
                file.delete()
                i++
            }
            fos.flush()
            fos.close()
            return true
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    private fun newRecorder() {
        recorder = MediaRecorder()

    }

    private fun prepareRecorder() {
        val directory = File(fileDirectory)
        require(!(!directory.exists() || !directory.isDirectory)) { "[AMRAudioRecorder] audioFileDirectory is a not valid directory!" }
        val filePath = directory.absolutePath + "/" + Date().time + ".amr"
        files.add(filePath)

//        val fileDescriptor = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
//        val byteArrayOutputStream = ByteArrayOutputStream()
//        val descriptors = ParcelFileDescriptor.createPipe()
//        val parcelRead = ParcelFileDescriptor(descriptors[0])
//        val parcelWrite = ParcelFileDescriptor(fileDescriptor)
//        val inputStream: InputStream = ParcelFileDescriptor.AutoCloseInputStream(parcelRead)
//        recorder!!.setOutputFile(parcelWrite.fileDescriptor)

        recorder!!.setOutputFile(filePath)
        recorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder!!.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
        recorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

//        Thread {
//            val bufferLength = 0
//            var bufferSize: Int
//            var audioData: ShortArray
//            var bufferReadResult: Int
//            var read: Int
//            val data = ByteArray(16384)
//            while (inputStream.read(data, 0, data.size).also { read = it } != -1) {
//                byteArrayOutputStream.write(data, 0, read)
//                "Data read".toLogConsole()
//            }
//            byteArrayOutputStream.flush()
//        }
    }

    private fun moveFile(inputPath: String, outputPath: String?) {
        var `in`: InputStream? = null
        var out: OutputStream? = null
        try {
            `in` = FileInputStream(inputPath)
            out = FileOutputStream(outputPath)
            val buffer = ByteArray(1024)
            var read: Int
            while (`in`.read(buffer).also { read = it } != -1) {
                out.write(buffer, 0, read)
            }
            `in`.close()
            `in` = null

            // write the output file
            out.flush()
            out.close()
            out = null

            // delete the original file
            File(inputPath).delete()
        } catch (fnfe1: FileNotFoundException) {
            Log.e("tag", fnfe1.message!!)
        } catch (e: Exception) {
            Log.e("tag", e.message!!)
        }
    }

    init {
        if (!fileDirectory.endsWith("/")) {
            fileDirectory += "/"
        }
        newRecorder()
    }
}
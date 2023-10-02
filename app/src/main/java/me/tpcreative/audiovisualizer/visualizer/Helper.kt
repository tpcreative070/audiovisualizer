package me.tpcreative.audiovisualizer.visualizer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import me.tpcreative.audiovisualizer.toLogConsole
import java.io.File

class Helper private constructor() {

    private var myService : AudioRecorderService? = null

    private var myConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            myService = (binder as AudioRecorderService.LocalBinder).service
            "Connected".toLogConsole()
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    fun startService(){
        doBindService()
    }

    fun stopService(){
        myService?.let {
            it.exitApp()
            App.instanceApp.unbindService(myConnection)
            myService = null
        }
    }

    val service : AudioRecorderService?
        get() {return myService}

    fun isRecording() : Boolean{
        return myService?.recorderRunning ?: false
    }
    fun requestStopRecording() {
        myService?.requestStopRecording()
    }

    fun createLocalNotification(){
        if (isRecording()){
            myService?.openLocalNotification()
        }
    }

    fun cancelLocalNotification(){
        if (isRecording()){
            myService?.cancelLocalNotification()
        }
    }

    private fun doBindService() {
        if (myService != null) {
            return
        }
        val intent: Intent?
        intent = Intent(App.instanceApp, AudioRecorderService::class.java)
        App.instanceApp.bindService(intent, myConnection, Context.BIND_AUTO_CREATE)
    }

    fun getAllFileInDirectory(directory: File): ArrayList<String> {
        val files = directory.listFiles()
        val listOfRecordings = ArrayList<String>()
        if (files != null) {
            for (file in files) {
                if (file != null) {
                    if (file.isDirectory) { // it is a folder...
                        getAllFileInDirectory(file)
                    } else { // it is a file...
                        listOfRecordings.add(file.absolutePath)
                    }
                }
            }
        }
        return listOfRecordings
    }

    val allRecordings: ArrayList<String>
        get() = getAllFileInDirectory(File(RECORDING_PATH))

    fun createRecordingFolder(): Boolean {
        return if (!File(RECORDING_PATH)
                .exists()
        ) {
            File(RECORDING_PATH).mkdir()
        } else {
            true
        }
    }

    fun createTempRecordingFolder(): Boolean {
        val tempFolder = "$RECORDING_PATH/temp"
        return if (!File(tempFolder).exists()) {
            File(tempFolder).mkdir()
        } else {
            true
        }
    }


    companion object {
        val helperInstance: Helper = Helper()
        val RECORDING_PATH = App.instanceApp.filesDir.path + "/CompSuite"
        const val LOAD_RECORDINGS = "Load Records"
    }
}
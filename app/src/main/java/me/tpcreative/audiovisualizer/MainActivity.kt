package me.tpcreative.audiovisualizer

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.app.ActivityCompat
import me.tpcreative.audiovisualizer.databinding.ActivityMainBinding
import me.tpcreative.audiovisualizer.visualizer.EnumEvent
import me.tpcreative.audiovisualizer.visualizer.Helper
import me.tpcreative.audiovisualizer.visualizer.MyCustomView
import me.tpcreative.audiovisualizer.visualizer.VisualizerData
import me.tpcreative.audiovisualizer.visualizer.showToast

class MainActivity : AppCompatActivity() {

    private var isStart  = false
    private lateinit var binding : ActivityMainBinding

    private lateinit var soundLevel : MyCustomView
    private  val handle = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        soundLevel = binding.barEqualizer
        checkAudioRecordPermission {
            Helper.helperInstance.startService()
        }

        binding.startRecord.setOnClickListener {
            if (isStart){
                stopRecord()
                binding.startRecord.text = "Start"
            }else{
                startRecord()
                binding.startRecord.text = "Stop"
            }
        }
    }

    private fun stopRecord(){
        if (!isStart){
            return
        }
        try {
            Helper.helperInstance.service?.stopRecord {
                soundLevel.onStop?.invoke()
                isStart = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "record failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun startRecord(){
        if (isStart){
            return
        }
        Helper.helperInstance.service?.onTimer  = {
            runOnUiThread {
                "Timer $it".toLogConsole()
                binding.tvTimer.text = it
            }
        }

        Helper.helperInstance.service?.onMaxAmplitude = {
            runOnUiThread {
                //val mData= VisualizerData()
                //soundLevel.onData?.invoke(mData)
            }
        }

        Helper.helperInstance.service?.startRecord {
            when(it){
                EnumEvent.START ->{
                    isStart = true
                }
                EnumEvent.START_RECORDING_FAILED ->{
                    Toast.makeText(this, "record failed", Toast.LENGTH_LONG).show()
                }
                EnumEvent.CREATE_FOLDER_FAILED -> {
                    Toast.makeText(this, "folder create failed", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private fun togglePlayBack() {
        checkAudioRecordPermission {
        }
    }

    private fun checkAudioRecordPermission(block: () -> Unit) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.RECORD_AUDIO
                )
            ) {
                showToast(R.string.no_permission)
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    PERMISSION_REQUEST_CODE
                )
            }
        } else {
            block()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    togglePlayBack()
                } else {
                    showToast(R.string.no_permission)
                }
                return
            }
        }
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        Helper.helperInstance.stopService()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }
}

fun String.toLogConsole(){
    Log.d("TAG_LOG",this)
}

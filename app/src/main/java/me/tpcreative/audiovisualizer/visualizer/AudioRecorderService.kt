package me.tpcreative.audiovisualizer.visualizer
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import me.tpcreative.audiovisualizer.BuildConfig
import me.tpcreative.audiovisualizer.R
import me.tpcreative.audiovisualizer.toLogConsole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Timer
import java.util.TimerTask

class AudioRecorderService : Service() {
    private lateinit var mediaRecorder: AMRAudioRecorder
    private lateinit var currentOutFile : String
    private val handlerVisualizer = Handler(Looper.getMainLooper())
    private var timer = Timer()
    private var count = 0
    private var pause = false
    private var isRecording = false
    private val mBinder = LocalBinder()
    var onTimer : ((String)->Unit)? = null
    var onMaxAmplitude : (((Float)->Unit)) ?= null
    var onRequestStopRecording : ((() -> Unit))? = null

    override fun onBind(intent: Intent?): IBinder {
        return mBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
    }

    override fun stopService(name: Intent?): Boolean {
        return super.stopService(name)
    }

    override fun onCreate() {
        super.onCreate()
    }

    inner class LocalBinder : Binder() {
        val service: AudioRecorderService
            get() = this@AudioRecorderService
    }

    fun requestStopRecording(){
        onRequestStopRecording?.invoke()
    }

    fun startRecord(callback: (EnumEvent) -> Unit) {
        if (Helper.helperInstance.createRecordingFolder()) {
            pause = true
            count = 0
            Helper.helperInstance.createTempRecordingFolder()
            currentOutFile =
                Helper.RECORDING_PATH + "/recording_" + currentTime + ".3gp"
            mediaRecorder = AMRAudioRecorder(Helper.RECORDING_PATH + "/temp")
            mediaRecorder.setAudioFile(currentOutFile)
            try {
                isRecording = true
                mediaRecorder.start()
                startTimer()
                handlerVisualizer.post(updateVisualizer)
                callback.invoke(EnumEvent.START)
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                isRecording = false
                callback.invoke(EnumEvent.START_RECORDING_FAILED)
            } catch (e: Exception) {
                isRecording = false
                e.printStackTrace()
                callback.invoke(EnumEvent.START_RECORDING_FAILED)
            }
        } else {
            isRecording = false
            callback.invoke(EnumEvent.CREATE_FOLDER_FAILED)
        }
    }

    fun stopRecord(callback : (String)-> Unit) {
        pause = false
        try {
            mediaRecorder.stop()
            callback.invoke(currentOutFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        timer.cancel()
        timer.purge()
        isRecording = false
        handlerVisualizer.removeCallbacks(updateVisualizer)
    }

    fun pauseRecord(callback: (Boolean) -> Unit) {
        if (!isRecording) {
            return
        }
        if (pause) {
            mediaRecorder.pause()
            timer.cancel()
            timer.purge()
            handlerVisualizer.removeCallbacks(updateVisualizer)
        } else {
            mediaRecorder.resume()
            startTimer()
            handlerVisualizer.post(updateVisualizer)
        }
        callback.invoke(pause)
        pause = !pause
    }
    private fun startTimer() {
        timer = Timer()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                count++
                onTimer?.invoke(getTimerRecord(count))
            }
        }, 0, 1000)
    }

    fun exitApp(){
        if (isRecording){
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }else{
            stopForeground(true)
        }
        stopSelf()
    }

    private var updateVisualizer: Runnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                mediaRecorder.getRecorder()?.let {
                    val maxAmp = it.maxAmplitude.toFloat()
                    "Max Amp $maxAmp".toLogConsole()
                    onMaxAmplitude?.invoke(maxAmp)
                }
                handlerVisualizer.postDelayed(this, REPEAT_INTERVAL.toLong())
            }
        }
    }

    fun openLocalNotification(){
        startForeground(ID_NOTIFICATION_SERVICE,createLocalNotification())
//        NotificationManagerCompat.from(this).apply {
//            notify(ID_NOTIFICATION_SERVICE,createLocalNotification())
//        }
    }

    fun cancelLocalNotification(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }else{
            stopForeground(true)
        }
//        NotificationManagerCompat.from(this).cancel(ID_NOTIFICATION_SERVICE)
    }

    val recorderRunning : Boolean get() {
        return isRecording
    }

    private fun createLocalNotification() : Notification{
        val intent = packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
        val pendingIntent = PendingIntent.getActivity(applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = notifyCompatBuilder
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle("Recording")
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
        return builder.build()
    }

    private val notifyCompatBuilder by lazy {
        initNotifyChanel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return@lazy NotificationCompat.Builder(this, CHANNEL_ID)
        } else {
            return@lazy  NotificationCompat.Builder(this, CHANNEL_ID)
        }
    }

    private val notifyManager: NotificationManager by lazy {
        this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun initNotifyChanel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notifyManager.getNotificationChannel(CHANNEL_ID) == null) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            )
            channel.enableVibration(true)
            notifyManager.createNotificationChannel(channel)
        }
    }


    @SuppressLint("DefaultLocale")
    fun getTimerRecord(count: Int): String {
        val s = count % 60
        val m = count / 60 % 60
        val h = m / 24
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    val currentTime: String
        get() {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HH_mm_ss")
            return dateFormat.format(Date())
        }

    companion object {
        const val REPEAT_INTERVAL = 40
        const val CHANNEL_ID = "9999_recording"
        const val CHANNEL_NAME = "recording"
        const val ID_NOTIFICATION_SERVICE = 8888
        const val ACTION_STOP_SERVICE = "stop_recording"
    }
}
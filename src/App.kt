package com.alfareed.flashnotify

import android.app.Activity
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.*

object Flasher {
    fun blink(ctx: Context) {
        val p = ctx.getSharedPreferences("p", 0)
        val n = p.getInt("n", 2)
        val ms = p.getInt("ms", 150).toLong()
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            val c = cm.getCameraCharacteristics(it)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return
        Thread {
            try {
                repeat(n) {
                    cm.setTorchMode(id, true); Thread.sleep(ms)
                    cm.setTorchMode(id, false); Thread.sleep(ms)
                }
            } catch (e: Exception) {}
        }.start()
    }
}

class NotifService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val p = getSharedPreferences("p", 0)
        if (!p.getBoolean("on", true)) return
        if (sbn.packageName == packageName) return
        val f = sbn.notification.flags
        if (f and Notification.FLAG_ONGOING_EVENT != 0) return
        if (f and Notification.FLAG_GROUP_SUMMARY != 0) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (p.getBoolean("off", true) && pm.isInteractive) return
        Flasher.blink(this)
    }
}

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("p", 0)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        fun label(t: String) = TextView(this).apply { text = t; textSize = 16f; setPadding(0, 32, 0, 8) }
        root.addView(TextView(this).apply { text = "Flash Notify"; textSize = 26f })
        root.addView(label("Flashes the back camera light when a notification arrives."))
        root.addView(Button(this).apply {
            text = "1. Allow notification access"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        })
        root.addView(Switch(this).apply {
            text = "Flash on notifications"; isChecked = p.getBoolean("on", true)
            setOnCheckedChangeListener { _, c -> p.edit().putBoolean("on", c).apply() }
        })
        root.addView(Switch(this).apply {
            text = "Only when screen is off"; isChecked = p.getBoolean("off", true)
            setOnCheckedChangeListener { _, c -> p.edit().putBoolean("off", c).apply() }
        })
        val nl = label("Blinks: ${p.getInt("n", 2)}")
        root.addView(nl)
        root.addView(SeekBar(this).apply {
            max = 4; progress = p.getInt("n", 2) - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, v: Int, u: Boolean) {
                    p.edit().putInt("n", v + 1).apply(); nl.text = "Blinks: ${v + 1}"
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })
        val sl = label("Speed: ${p.getInt("ms", 150)} ms")
        root.addView(sl)
        root.addView(SeekBar(this).apply {
            max = 440; progress = p.getInt("ms", 150) - 60
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, v: Int, u: Boolean) {
                    p.edit().putInt("ms", v + 60).apply(); sl.text = "Speed: ${v + 60} ms"
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })
        root.addView(Button(this).apply {
            text = "2. Test flash"
            setOnClickListener { Flasher.blink(this@MainActivity) }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}

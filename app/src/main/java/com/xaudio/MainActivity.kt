package com.xaudio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sin
import kotlin.math.PI

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbManager
    private lateinit var tvStatus: TextView
    private lateinit var btnPlay: Button
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        tvStatus = findViewById(R.id.tvStatus)
        btnPlay = findViewById(R.id.btnPlay)

        btnPlay.setOnClickListener {
            if (!isPlaying) {
                findAndConnectDAC()
            } else {
                isPlaying = false
                btnPlay.text = "DETEKSI & BYPASS"
            }
        }
    }

    private fun findAndConnectDAC() {
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            tvStatus.text = "DAC tidak ditemukan"
            return
        }
        val device = deviceList.values.first()
        if (usbManager.hasPermission(device)) {
            openAndPlay(device)
        } else {
            val intent = PendingIntent.getBroadcast(this, 0, Intent("com.xaudio.USB_PERMISSION").apply { setPackage(packageName) }, PendingIntent.FLAG_MUTABLE)
            usbManager.requestPermission(device, intent)
        }
    }

    private fun openAndPlay(device: UsbDevice) {
        val connection = usbManager.openDevice(device) ?: return
        val intf = device.getInterface(2) // Berdasarkan screenshotmu, Intf 2 atau 4 yang punya EP 1
        val endpoint = intf.getEndpoint(0) // Endpoint OUT
        
        connection.claimInterface(intf, true)
        isPlaying = true
        btnPlay.text = "STOP AUDIO"
        tvStatus.text = "[>>>] MENEMBAKKAN SINE WAVE 440Hz KE DAC..."

        // Thread khusus untuk streaming data agar UI tidak freeze
        Thread {
            val sampleRate = 44100
            val frequency = 440.0 // Nada A
            var phase = 0.0
            val bufferSize = 384 // Sesuai Max Packet Size kamu
            val buffer = ByteArray(bufferSize)

            while (isPlaying) {
                for (i in 0 until bufferSize step 2) {
                    val value = (sin(phase) * 32767).toInt()
                    buffer[i] = (value and 0xFF).toByte()
                    buffer[i + 1] = (value shr 8).toByte()
                    phase += 2.0 * PI * frequency / sampleRate
                }
                // Tembak langsung ke Hardware!
                connection.bulkTransfer(endpoint, buffer, bufferSize, 100)
            }
            connection.releaseInterface(intf)
            connection.close()
        }.start()
    }
}

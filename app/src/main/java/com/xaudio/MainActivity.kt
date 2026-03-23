package com.xaudio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
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
            tvStatus.text = "DAC Tidak Terdeteksi"
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
        
        // Cari Interface yang punya Endpoint Audio OUT
        // Kita coba Interface 4 dulu karena di log kamu Max Packet Size-nya paling gede (384)
        val targetIntf = device.getInterface(device.interfaceCount - 1) 
        val endpoint = targetIntf.getEndpoint(0)

        try {
            connection.claimInterface(targetIntf, true)

            // MANTRA: Set Alternate Setting ke 1 (Mode Aktif)
            // Banyak DAC butuh ini buat 'buka gerbang' audio
            connection.controlTransfer(0x01, 0x0B, 1, targetIntf.id, null, 0, 100)

            isPlaying = true
            btnPlay.text = "STOP"
            tvStatus.text = "STREAMING KE:\nIntf: ${targetIntf.id}\nEP: ${endpoint.address}\nPacket: ${endpoint.maxPacketSize}"

            Thread {
                val sampleRate = 44100
                var phase = 0.0
                val freq = 440.0
                val bufferSize = endpoint.maxPacketSize
                val buffer = ByteArray(bufferSize)

                while (isPlaying) {
                    for (i in 0 until bufferSize step 2) {
                        val value = (sin(phase) * 32767).toInt()
                        buffer[i] = (value and 0xFF).toByte()
                        buffer[i + 1] = (value shr 8).toByte()
                        phase += 2.0 * PI * freq / sampleRate
                    }
                    // Pakai bulkTransfer karena Android USB Host API menyatukan Isoc ke sini
                    connection.bulkTransfer(endpoint, buffer, bufferSize, 0)
                }
                connection.releaseInterface(targetIntf)
                connection.close()
            }.start()
        } catch (e: Exception) {
            tvStatus.text = "Error: ${e.message}"
        }
    }
}

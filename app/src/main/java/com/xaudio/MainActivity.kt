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
            if (!isPlaying) findAndConnectDAC()
            else {
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
        
        var audioIntf: android.hardware.usb.UsbInterface? = null
        var audioEp: android.hardware.usb.UsbEndpoint? = null

        // SCAN ULANG: Cari Interface yang punya Endpoint OUT (Arah ke Earphone)
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            for (j in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(j)
                // UsbConstants.USB_DIR_OUT = 0 (Data keluar dari HP ke USB)
                if (ep.direction == UsbConstants.USB_DIR_OUT) {
                    audioIntf = intf
                    audioEp = ep
                }
            }
        }

        if (audioIntf != null && audioEp != null) {
            connection.claimInterface(audioIntf, true)
            
            // PAKSA AKTIF: Pindahkan ke Alternate Setting 1
            connection.controlTransfer(0x01, 0x0B, 1, audioIntf.id, null, 0, 100)

            isPlaying = true
            btnPlay.text = "STOP AUDIO"
            tvStatus.text = "[SUCCESS]\nIntf: ${audioIntf.id}\nEP: ${audioEp.address} (DIR_OUT)\nPacket: ${audioEp.maxPacketSize}"

            Thread {
                val sampleRate = 44100
                val freq = 440.0
                var phase = 0.0
                val bufferSize = audioEp.maxPacketSize
                val buffer = ByteArray(bufferSize)

                while (isPlaying) {
                    for (i in 0 until bufferSize step 2) {
                        val value = (sin(phase) * 32767).toInt()
                        buffer[i] = (value and 0xFF).toByte()
                        buffer[i + 1] = (value shr 8).toByte()
                        phase += 2.0 * PI * freq / sampleRate
                    }
                    // Transfer data ke hardware
                    connection.bulkTransfer(audioEp, buffer, bufferSize, 0)
                }
                connection.releaseInterface(audioIntf)
                connection.close()
            }.start()
        } else {
            tvStatus.text = "Gagal nemu Endpoint OUT.\nCoba cabut colok DAC."
            connection.close()
        }
    }
}

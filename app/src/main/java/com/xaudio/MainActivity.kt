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
        
        var targetIntf: android.hardware.usb.UsbInterface? = null
        var targetEp: android.hardware.usb.UsbEndpoint? = null

        // Kita cari interface yang beneran buat Streaming (Subclass 2)
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == 1 && intf.interfaceSubclass == 2) {
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        targetIntf = intf
                        targetEp = ep
                    }
                }
            }
        }

        if (targetIntf != null && targetEp != null) {
            connection.claimInterface(targetIntf, true)

            // COBA MANTRA 2 (Alternate Setting 2)
            // Banyak DAC 384kHz cuma mau bunyi kalau diset ke mode bandwidth tertinggi (2)
            val requestType = 0x01 // Host to Interface
            val request = 0x0B     // SET_INTERFACE
            val value = 2          // Alternate Setting 2 (Ubah ke 1 atau 3 kalau gagal)
            val index = targetIntf.id
            
            connection.controlTransfer(requestType, request, value, index, null, 0, 100)

            isPlaying = true
            btnPlay.text = "STOP AUDIO"
            tvStatus.text = "FORCE BYPASS MODE 2\nIntf: ${targetIntf.id}\nEP: ${targetEp.address}\nPacket: ${targetEp.maxPacketSize}"

            Thread {
                val sampleRate = 44100
                val freq = 440.0
                var phase = 0.0
                val bufferSize = targetEp.maxPacketSize
                val buffer = ByteArray(bufferSize)

                while (isPlaying) {
                    for (i in 0 until bufferSize step 2) {
                        val valueSine = (sin(phase) * 32767).toInt()
                        buffer[i] = (valueSine and 0xFF).toByte()
                        buffer[i + 1] = (valueSine shr 8).toByte()
                        phase += 2.0 * PI * freq / sampleRate
                    }
                    // Tembak tanpa henti
                    connection.bulkTransfer(targetEp, buffer, bufferSize, 0)
                }
                connection.releaseInterface(targetIntf)
                connection.close()
            }.start()
        } else {
            tvStatus.text = "Jalur Streaming Tidak Ditemukan"
            connection.close()
        }
    }
}

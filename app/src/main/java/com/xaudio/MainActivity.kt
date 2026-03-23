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

    // Kita tetap deklarasikan agar Library C++ yang kita buat kemarin tidak error saat dimuat
    external fun startBypass(fd: Int, usbFs: String): String

    companion object {
        init {
            try {
                System.loadLibrary("xaudio_engine")
            } catch (e: Exception) {
                // Library dimuat untuk keperluan logging/check di tahap sebelumnya
            }
        }
    }

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
                tvStatus.text = "Audio Berhenti."
            }
        }
    }

    private fun findAndConnectDAC() {
        try {
            val deviceList = usbManager.deviceList
            if (deviceList.isEmpty()) {
                tvStatus.text = "[!] DAC tidak terdeteksi.\nPastikan OTG aktif."
                return
            }

            // Ambil perangkat pertama (CX31993 kamu)
            val device = deviceList.values.first()

            if (usbManager.hasPermission(device)) {
                openAndPlay(device)
            } else {
                val intent = Intent("com.xaudio.USB_PERMISSION")
                intent.setPackage(packageName)
                val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_MUTABLE)
                usbManager.requestPermission(device, pendingIntent)
                tvStatus.text = "[*] Meminta izin USB..."
            }
        } catch (e: Exception) {
            tvStatus.text = "Error: ${e.message}"
        }
    }

    private fun openAndPlay(device: UsbDevice) {
        val connection = usbManager.openDevice(device) ?: return
        
        var targetInterface: android.hardware.usb.UsbInterface? = null
        var targetEndpoint: android.hardware.usb.UsbEndpoint? = null

        // Mencari Interface Audio Streaming (Class 1, Subclass 2)
        // Dan mencari Endpoint OUT Isochronous
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == 1 && intf.interfaceSubclass == 2) {
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.direction == UsbConstants.USB_DIR_OUT && 
                        ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC) {
                        targetInterface = intf
                        targetEndpoint = ep
                    }
                }
            }
        }

        if (targetInterface != null && targetEndpoint != null) {
            // 1. Klaim Interface (Kunci dari Android)
            connection.claimInterface(targetInterface, true)
            
            // 2. MANTRA BANGUN (Set Interface / Alternate Setting)
            // Ini memerintahkan DAC untuk pindah dari mode 'diam' ke mode 'streaming'
            // bRequest = 0x0B (SET_INTERFACE), wValue = 1 (Active Mode)
            connection.controlTransfer(0x01, 0x0B, 1, targetInterface.id, null, 0, 100)

            isPlaying = true
            btnPlay.text = "STOP AUDIO"
            tvStatus.text = "[>>>] MENEMBAKKAN SINE WAVE 440Hz...\n" +
                           "Interface: ${targetInterface.id}\n" +
                           "Endpoint: ${targetEndpoint.address}\n" +
                           "Max Packet: ${targetEndpoint.maxPacketSize} bytes"

            // 3. Thread Streaming
            Thread {
                val sampleRate = 44100
                val frequency = 440.0 // Nada A4
                var phase = 0.0
                val bufferSize = targetEndpoint.maxPacketSize
                val buffer = ByteArray(bufferSize)

                while (isPlaying) {
                    for (i in 0 until bufferSize step 2) {
                        // Rumus Matematika Sine Wave (PCM 16-bit Little Endian)
                        val value = (sin(phase) * 32767).toInt()
                        buffer[i] = (value and 0xFF).toByte()
                        buffer[i + 1] = (value shr 8).toByte()
                        
                        phase += 2.0 * PI * frequency / sampleRate
                        if (phase > 2.0 * PI) phase -= 2.0 * PI
                    }
                    
                    // Tembak data mentah ke DAC
                    // Timeout diset pendek (50ms) agar tidak ada lag
                    connection.bulkTransfer(targetEndpoint, buffer, bufferSize, 50)
                }

                // Bersihkan saat berhenti
                connection.releaseInterface(targetInterface)
                connection.close()
            }.start()
        } else {
            tvStatus.text = "[-] Gagal menemukan Jalur Audio OUT yang cocok."
            connection.close()
        }
    }
}

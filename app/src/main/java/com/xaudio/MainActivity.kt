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
                tvStatus.text = "Audio Berhenti."
            }
        }
    }

    private fun findAndConnectDAC() {
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            tvStatus.text = "DAC Tidak Terdeteksi.\nPastikan OTG aktif."
            return
        }
        val device = deviceList.values.first()
        if (usbManager.hasPermission(device)) {
            openAndPlay(device)
        } else {
            val intent = PendingIntent.getBroadcast(this, 0, Intent("com.xaudio.USB_PERMISSION").apply { setPackage(packageName) }, PendingIntent.FLAG_MUTABLE)
            usbManager.requestPermission(device, intent)
            tvStatus.text = "[*] Meminta izin USB..."
        }
    }

    private fun openAndPlay(device: UsbDevice) {
        val connection = usbManager.openDevice(device) ?: return
        
        // SCAN ULANG: Cari Interface sakti yang punya Endpoint Audio OUT
        // Kita simpan Interface-nya buat dikasih mantra bangun
        var audioIntf: android.hardware.usb.UsbInterface? = null
        var audioEp: android.hardware.usb.UsbEndpoint? = null

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            for (j in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(j)
                // UsbConstants.USB_DIR_OUT = 0
                if (ep.direction == UsbConstants.USB_DIR_OUT) {
                    audioIntf = intf
                    audioEp = ep
                }
            }
        }

        if (audioIntf != null && audioEp != null) {
            // A. KLAIM/KUNCI INTERFACE (Wajib)
            connection.claimInterface(audioIntf, true)
            
            // B. BOM MANTRA (SET_INTERFACE) - PENTING!
            // Kita coba bombong DAC-nya dengan 3 mantra bangun yang beda:
            // Mantra bRequest=0x0B (SET_INTERFACE)
            
            // Mantra 1: Coba Mode Aktif 1
            connection.controlTransfer(0x01, 0x0B, 1, audioIntf.id, null, 0, 100)
            
            // Mantra 2: Coba Mode Aktif 2 (Beberapa DAC High-Res pakai ini)
            // connection.controlTransfer(0x01, 0x0B, 2, audioIntf.id, null, 0, 100)
            
            // Mantra 3: Coba Mode Aktif 3
            // connection.controlTransfer(0x01, 0x0B, 3, audioIntf.id, null, 0, 100)

            isPlaying = true
            btnPlay.text = "STOP AUDIO"
            tvStatus.text = "[SUCCESS BYPASS]\n" +
                           "Intf: ${audioIntf.id}\n" +
                           "EP: ${audioEp.address} (DIR_OUT)\n" +
                           "Packet: ${audioEp.maxPacketSize} bytes\n" +
                           "Mantra Bangun 0x0B Terkirim."

            // C. THREAD STREAMING PCM (Data lagu asli/Sine Wave)
            Thread {
                val sampleRate = 44100
                val freq = 440.0 // Nada A (Sine Wave murni)
                var phase = 0.0
                val bufferSize = audioEp.maxPacketSize
                val buffer = ByteArray(bufferSize)

                while (isPlaying) {
                    // Generate data audio Sine Wave di Kotlin (Pencitraan PCM)
                    for (i in 0 until bufferSize step 2) {
                        // Formula Matematika Suara
                        val value = (sin(phase) * 32767).toInt()
                        buffer[i] = (value and 0xFF).toByte()     // Little Endian
                        buffer[i + 1] = (value shr 8).toByte()
                        
                        phase += 2.0 * PI * freq / sampleRate
                        if (phase > 2.0 * PI) phase -= 2.0 * PI // Reset phase biar suaranya kontinu
                    }
                    
                    // Tembak data audio mentah ke DAC
                    // Pakai bulkTransfer karena Android USB API menyatukan ISO Transfer ke sini
                    // Timeout diset 0 (Non-blocking) agar tidak putus-putus
                    connection.bulkTransfer(audioEp, buffer, bufferSize, 0)
                }

                // Bersihkan saat berhenti
                connection.releaseInterface(audioIntf)
                connection.close()
            }.start()
        } else {
            tvStatus.text = "[-] Gagal menemukan Jalur Audio OUT.\nCoba cabut-colok DAC."
            connection.close()
        }
    }
}

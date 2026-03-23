package com.xaudio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbManager
    private lateinit var tvStatus: TextView
    private lateinit var btnPlay: Button

    external fun startBypass(fd: Int, usbFs: String): String

    companion object {
        init {
            try {
                System.loadLibrary("xaudio_engine")
            } catch (e: Exception) {
                // Biar kalau C++ nya gagal muat, ketahuan
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
            findAndConnectDAC()
        }
    }

    private fun findAndConnectDAC() {
        try {
            val deviceList = usbManager.deviceList
            if (deviceList.isEmpty()) {
                tvStatus.text = "[!] DAC tidak terdeteksi.\nPastikan dicolok & OTG nyala."
                return
            }

            val device = deviceList.values.first()
            tvStatus.text = "[*] Menemukan: ${device.productName}\nMemeriksa izin..."

            if (usbManager.hasPermission(device)) {
                openDevice(device)
            } else {
                val intent = Intent("com.xaudio.USB_PERMISSION")
                // INI DIA OBAT ANTI FORCE CLOSE UNTUK ANDROID 14:
                intent.setPackage(packageName) 
                
                val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_MUTABLE)
                usbManager.requestPermission(device, pendingIntent)
                tvStatus.text = "[*] Meminta izin USB...\nKlik tombol lagi setelah diizinkan."
            }
        } catch (e: Exception) {
            // Kalau error, cetak ke layar biar gampang di-debug
            tvStatus.text = "ERROR Kotlin: ${e.message}"
        }
    }

    private fun openDevice(device: UsbDevice) {
        try {
            val connection = usbManager.openDevice(device)
            if (connection != null) {
                val fd = connection.fileDescriptor
                val usbFs = device.deviceName 
                
                tvStatus.text = "[+] Koneksi sukses!\nFD: $fd | Path: $usbFs\nMengeksekusi C++ Driver..."
                
                val result = startBypass(fd, usbFs)
                tvStatus.text = result
            } else {
                tvStatus.text = "[-] Gagal buka koneksi ke DAC. Coba cabut-colok."
            }
        } catch (e: Exception) {
            tvStatus.text = "ERROR JNI/C++: ${e.message}"
        }
    }
}

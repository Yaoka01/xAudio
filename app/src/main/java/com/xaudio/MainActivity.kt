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

    // Deklarasi fungsi ajaib yang nanti akan kita tulis di C++
    external fun startBypass(fd: Int, usbFs: String): String

    companion object {
        init {
            // Memanggil file driver C++ kita (nanti kita buat di Tahap 5)
            System.loadLibrary("xaudio_engine")
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
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            tvStatus.text = "[!] DAC tidak terdeteksi.\nPastikan OTG nyala."
            return
        }

        // Ambil USB pertama yang nancep (asumsinya ini dongle CX31993 kamu)
        val device = deviceList.values.first()
        tvStatus.text = "[*] Menemukan: ${device.productName}\nMemeriksa izin..."

        if (usbManager.hasPermission(device)) {
            openDevice(device)
        } else {
            // Minta izin ke Android buat ngebajak USB-nya
            val intent = Intent("com.xaudio.USB_PERMISSION")
            val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_MUTABLE)
            usbManager.requestPermission(device, pendingIntent)
            tvStatus.text = "[*] Meminta izin USB...\nKlik tombol lagi setelah diizinkan."
        }
    }

    private fun openDevice(device: UsbDevice) {
        val connection = usbManager.openDevice(device)
        if (connection != null) {
            // INI DIA KUNCINYA! Kita curi File Descriptor (FD) dari Android
            val fd = connection.fileDescriptor
            // Kita juga butuh path device-nya buat driver C++ (libusb)
            val usbFs = device.deviceName 
            
            tvStatus.text = "[+] Koneksi sukses!\nFD: $fd | Path: $usbFs\nMengeksekusi C++ Driver..."
            
            // Lempar kunci FD-nya ke kode C++
            val result = startBypass(fd, usbFs)
            tvStatus.text = result
        } else {
            tvStatus.text = "[-] Gagal membuka koneksi ke DAC."
        }
    }
}

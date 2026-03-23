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

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbManager
    private lateinit var tvStatus: TextView
    private lateinit var btnPlay: Button

    // Deklarasi fungsi C++ (tetap dibiarkan agar tidak error, meski belum dipanggil di tahap ini)
    external fun startBypass(fd: Int, usbFs: String): String

    companion object {
        init {
            try {
                System.loadLibrary("xaudio_engine")
            } catch (e: Exception) {
                // Biarkan kosong
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
                intent.setPackage(packageName) 
                
                val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_MUTABLE)
                usbManager.requestPermission(device, pendingIntent)
                tvStatus.text = "[*] Meminta izin USB...\nKlik tombol lagi setelah diizinkan."
            }
        } catch (e: Exception) {
            tvStatus.text = "ERROR Kotlin: ${e.message}"
        }
    }

    private fun openDevice(device: UsbDevice) {
        try {
            val connection = usbManager.openDevice(device)
            if (connection != null) {
                var audioEndpointAddress = -1
                var targetInterface: android.hardware.usb.UsbInterface? = null
                val logInfo = java.lang.StringBuilder()

                logInfo.append("[+] Koneksi sukses!\nMemindai Interface DAC...\n\n")

                // Looping untuk mencari pintu "Audio Streaming"
                for (i in 0 until device.interfaceCount) {
                    val intf = device.getInterface(i)
                    
                    // 1 = Audio Class, 2 = Audio Streaming Subclass
                    if (intf.interfaceClass == 1 && intf.interfaceSubclass == 2) {
                        logInfo.append("-> Ditemukan Audio Streaming (Intf $i)\n")
                        
                        // Cari "Mulut" (Endpoint) yang arahnya KELUAR (Out)
                        for (j in 0 until intf.endpointCount) {
                            val ep = intf.getEndpoint(j)
                            
                            // Cek apakah ini Isochronous (Realtime) dan arahnya OUT (ke DAC)
                            if (ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC) {
                                audioEndpointAddress = ep.endpointAddress
                                targetInterface = intf
                                logInfo.append("   [!] Endpoint Audio OUT: $audioEndpointAddress\n")
                                logInfo.append("   Max Packet Size: ${ep.maxPacketSize} bytes\n")
                            }
                        }
                    }
                }

                if (targetInterface != null && audioEndpointAddress != -1) {
                    // KUNCI PINTUNYA! Biar Android nggak bisa ikut campur
                    val claimed = connection.claimInterface(targetInterface, true)
                    if (claimed) {
                        logInfo.append("\n[+] Interface berhasil dikunci!\n")
                        logInfo.append("Siap menembakkan data ke Endpoint: $audioEndpointAddress")
                    } else {
                        logInfo.append("\n[-] Gagal mengunci Interface. Mungkin sedang dipakai sistem.")
                    }
                } else {
                    logInfo.append("\n[-] Tidak menemukan Endpoint Audio Streaming.")
                }

                tvStatus.text = logInfo.toString()
                
            } else {
                tvStatus.text = "[-] Gagal buka koneksi ke DAC."
            }
        } catch (e: Exception) {
            tvStatus.text = "ERROR: ${e.message}"
        }
    }
}

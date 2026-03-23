package com.xaudio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView

    companion object {
        init {
            // Nama ini HARUS sama dengan yang ada di CMakeLists.txt
            System.loadLibrary("xaudio_engine")
        }
    }

    // Fungsi Bridge ke C++
    external fun startBypass(fd: Int, interfaceId: Int, endpointAddr: Int): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // UI Programmatic sederhana biar gak error layout
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(50, 50, 50, 50)
        }
        tvStatus = TextView(this).apply { 
            text = "Status: Siap\nColok DAC & Klik Mulai"; 
            textSize = 18f; textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER 
        }
        val btnPlay = Button(this).apply { text = "DETEKSI & BYPASS" }
        
        layout.addView(tvStatus)
        layout.addView(btnPlay)
        setContentView(layout)

        btnPlay.setOnClickListener {
            executeBypass()
        }
    }

    private fun executeBypass() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val device = manager.deviceList.values.firstOrNull()

        if (device == null) {
            tvStatus.text = "[-] DAC Tidak Terdeteksi!"
            return
        }

        if (manager.hasPermission(device)) {
            val connection = manager.openDevice(device)
            if (connection != null) {
                tvStatus.text = "[*] Menjalankan Native Engine..."
                
                Thread {
                    var targetIntfId = -1
                    var targetEpAddr = -1

                    // Cari Jalur Audio OUT
                    for (i in 0 until device.interfaceCount) {
                        val intf = device.getInterface(i)
                        for (j in 0 until intf.endpointCount) {
                            val ep = intf.getEndpoint(j)
                            if (ep.direction == UsbConstants.USB_DIR_OUT) {
                                targetIntfId = intf.id
                                targetEpAddr = ep.address
                            }
                        }
                    }

                    if (targetIntfId != -1) {
                        // Panggil C++
                        val result = startBypass(connection.fileDescriptor, targetIntfId, targetEpAddr)
                        runOnUiThread { 
                            tvStatus.text = result
                            connection.close()
                        }
                    } else {
                        runOnUiThread { tvStatus.text = "[-] Jalur OUT Tidak Ditemukan" }
                    }
                }.start()
            }
        } else {
            val intent = PendingIntent.getBroadcast(this, 0, Intent("USB_PERMISSION"), PendingIntent.FLAG_MUTABLE)
            manager.requestPermission(device, intent)
            tvStatus.text = "[*] Menunggu Izin USB..."
        }
    }
}

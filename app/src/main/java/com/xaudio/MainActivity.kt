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

    external fun startBypass(fd: Int, interfaceId: Int, endpointAddr: Int): String

    companion object {
        init { System.loadLibrary("xaudio_engine") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        tvStatus = findViewById(R.id.tvStatus); btnPlay = findViewById(R.id.btnPlay)

        btnPlay.setOnClickListener { findAndConnectDAC() }
    }

    private fun findAndConnectDAC() {
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) { tvStatus.text = "DAC Tidak Ada"; return }
        val device = deviceList.values.first()
        
        if (usbManager.hasPermission(device)) {
            val connection = usbManager.openDevice(device) ?: return
            
            // Ambil Interface & EP OUT secara otomatis
            var intfId = -1; var epAddr = -1
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        intfId = intf.id; epAddr = ep.address
                    }
                }
            }

            if (intfId != -1) {
                tvStatus.text = "Executing Kernel Bypass...\nIntf: $intfId, EP: $epAddr"
                // Panggil "Algojo" C++ kita
                val result = startBypass(connection.fileDescriptor, intfId, epAddr)
                tvStatus.text = result
            }
        } else {
            val intent = PendingIntent.getBroadcast(this, 0, Intent("com.xaudio.USB_PERMISSION").apply { setPackage(packageName) }, PendingIntent.FLAG_MUTABLE)
            usbManager.requestPermission(device, intent)
        }
    }
}

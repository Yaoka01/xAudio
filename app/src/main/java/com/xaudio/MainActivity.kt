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

    // Fungsi JNI ke C++
    external fun startBypass(fd: Int, interfaceId: Int, endpointAddr: Int): String

    companion object {
        init {
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
            tvStatus.text = "DAC Tidak Terdeteksi.\nCoba cabut colok."
            return
        }

        val device = deviceList.values.first()
        
        if (usbManager.hasPermission(device)) {
            runNativeEngine(device)
        } else {
            val intent = PendingIntent.getBroadcast(
                this, 0, 
                Intent("com.xaudio.USB_PERMISSION").apply { setPackage(packageName) }, 
                PendingIntent.FLAG_MUTABLE
            )
            usbManager.requestPermission(device, intent)
            tvStatus.text = "[*] Menunggu Izin USB..."
        }
    }

    private fun runNativeEngine(device: UsbDevice) {
        val connection = usbManager.openDevice(device) ?: return
        
        var targetInterfaceId = -1
        var targetEndpointAddr = -1

        // SCAN: Mencari Interface Audio Streaming (Class 1, Subclass 2)
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            // UAC (USB Audio Class) Standard: Class 1, Subclass 2 = Streaming
            if (intf.interfaceClass == 1 && intf.interfaceSubclass == 2) {
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    // Arah OUT (Data dari HP ke DAC)
                    if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        targetInterfaceId = intf.id
                        targetEndpointAddr = ep.address
                    }
                }
            }
        }

        if (targetInterfaceId != -1 && targetEndpointAddr != -1) {
            tvStatus.text = "ENGINE STARTING...\n" +
                           "Interface: $targetInterfaceId\n" +
                           "Endpoint: $targetEndpointAddr\n" +
                           "Metode: URB Asynchronous"

            // Jalankan Algojo C++ di Background Thread agar UI tidak Lag
            Thread {
                val result = startBypass(connection.fileDescriptor, targetInterfaceId, targetEndpointAddr)
                runOnUiThread {
                    tvStatus.text = result
                }
            }.start()
        } else {
            tvStatus.text = "[-] Gagal menemukan Jalur Audio Streaming."
            connection.close()
        }
    }
}

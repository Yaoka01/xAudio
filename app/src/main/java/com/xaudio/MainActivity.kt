package com.xaudio

import android.app.Activity
import android.hardware.usb.*
import android.os.Bundle
import android.util.Log

class MainActivity : Activity() {

    external fun startNativeAudio(fd: Int)

    companion object {
        init {
            System.loadLibrary("native-audio")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = getSystemService(USB_SERVICE) as UsbManager
        val deviceList = manager.deviceList

        if (deviceList.isEmpty()) {
            Log.e("USB", "No USB devices found")
            return
        }

        for (device in deviceList.values) {
            Log.d("USB", "Found device: $device")

            if (device.deviceClass == UsbConstants.USB_CLASS_AUDIO) {

                val connection = manager.openDevice(device)
                if (connection == null) {
                    Log.e("USB", "Permission denied")
                    return
                }

                val intf = device.getInterface(0)
                connection.claimInterface(intf, true)

                Log.d("USB", "Interface claimed")

                startNativeAudio(connection.fileDescriptor)
                break
            }
        }
    }
}

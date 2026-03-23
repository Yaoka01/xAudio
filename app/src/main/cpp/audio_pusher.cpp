#include <jni.h>
#include <string>
#include <unistd.h>
#include <android/log.h>
#include <linux/usb/ch9.h>
#include <sstream>
#include <iomanip>

#define LOG_TAG "xAudio_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_xaudio_MainActivity_startBypass(
        JNIEnv* env,
        jobject /* this */,
        jint fd,
        jstring usbFs) {

    const char *path = env->GetStringUTFChars(usbFs, nullptr);

    if (fd < 0) {
        env->ReleaseStringUTFChars(usbFs, path);
        return env->NewStringUTF("[-] ERROR: File Descriptor tidak valid!");
    }

    LOGI("Mengakses memori DAC di FD: %d", fd);

    // 1. Kembalikan pointer pembacaan ke awal memori USB
    lseek(fd, 0, SEEK_SET);

    // 2. Siapkan struktur data bawaan Linux untuk menampung info perangkat USB
    struct usb_device_descriptor desc;

    // 3. BACA OTAKNYA! (Baca 18 byte pertama dari File Descriptor)
    int bytes_read = read(fd, &desc, sizeof(desc));

    std::stringstream ss;

    if (bytes_read == sizeof(desc)) {
        ss << "[+] BERHASIL MEMBACA OTAK DAC! \n\n";
        
        // Cetak hasilnya dalam format Hexadesimal dan Desimal
        ss << "Vendor ID   : 0x" << std::hex << std::setfill('0') << std::setw(4) << desc.idVendor << "\n";
        ss << "Product ID  : 0x" << std::hex << std::setfill('0') << std::setw(4) << desc.idProduct << "\n";
        ss << "USB Class   : " << std::dec << (int)desc.bDeviceClass << " (0 = Audio/Composite)\n";
        ss << "Max Packet  : " << (int)desc.bMaxPacketSize0 << " bytes\n";
        ss << "Num Configs : " << (int)desc.bNumConfigurations << "\n\n";
        ss << "[!] C++ siap memetakan Endpoint Audio.";
    } else {
        ss << "[-] GAGAL MEMBACA DESCRIPTOR.\nBytes yang terbaca: " << bytes_read;
    }

    env->ReleaseStringUTFChars(usbFs, path);
    return env->NewStringUTF(ss.str().c_str());
}

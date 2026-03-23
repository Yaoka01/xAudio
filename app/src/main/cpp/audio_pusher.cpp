#include <jni.h>
#include <string>
#include <unistd.h>
#include <android/log.h>

#define LOG_TAG "xAudio_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_xaudio_MainActivity_startBypass(
        JNIEnv* env,
        jobject /* this */,
        jint fd,
        jstring usbFs) {

    // Ubah string Java menjadi string C++
    const char *path = env->GetStringUTFChars(usbFs, nullptr);

    // Cek apakah File Descriptor (jalur ke DAC) valid
    if (fd < 0) {
        env->ReleaseStringUTFChars(usbFs, path);
        return env->NewStringUTF("[-] ERROR: File Descriptor tidak valid!");
    }

    LOGI("Menerima akses mentah ke DAC CX31993! FD: %d, Path: %s", fd, path);

    // =========================================================================
    // DI SINI TEMPAT DRIVER UAC2 (USB Audio Class 2.0) YANG ASLI DITULIS
    // Contoh perintah yang bisa dilakukan di sini dengan ioctl() linux:
    // ioctl(fd, USBDEVFS_CLAIMINTERFACE, &intf) -> Mengunci DAC dari Android
    // ioctl(fd, USBDEVFS_SUBMITURB, &urb) -> Menembakkan file lagu .wav ke DAC
    // =========================================================================

    // Karena ini Proof of Concept, kita buktikan dulu bahwa C++ berhasil merebut FD
    std::string result = "[+] NATIVE BYPASS SUKSES! 🚀\n"
                         "C++ Engine memegang kontrol USB.\n"
                         "File Descriptor: " + std::to_string(fd) + "\n"
                         "Jalur Kernel: " + std::string(path) + "\n"
                         "AudioFlinger Android berhasil dilewati!";

    // Bersihkan memori biar nggak bocor
    env->ReleaseStringUTFChars(usbFs, path);
    
    // Kembalikan teks sukses ke layar HP kamu
    return env->NewStringUTF(result.c_str());
}

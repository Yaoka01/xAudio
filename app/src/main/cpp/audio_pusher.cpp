#include <jni.h>
#include <string>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <android/log.h>
#include <math.h>

#define LOG_TAG "xAudio_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_xaudio_MainActivity_startBypass(
        JNIEnv* env,
        jobject /* this */,
        jint fd,
        jint interface_id,
        jint endpoint_addr) {

    // 1. PAKSA KLAIM INTERFACE (Menendang Driver Sistem iQOO)
    if (ioctl(fd, USBDEVFS_CLAIMINTERFACE, &interface_id) < 0) {
        // Jika gagal klaim biasa, kita paksa lepas driver kernelnya
        struct usbdevfs_disconnectif disc;
        disc.interface = interface_id;
        disc.flags = USBDEVFS_DISCONNECT_IF_DRIVER_IF_BOUND;
        ioctl(fd, USBDEVFS_DISCONNECTIF, &disc);
        
        // Coba klaim lagi setelah ditendang
        if (ioctl(fd, USBDEVFS_CLAIMINTERFACE, &interface_id) < 0) {
            return env->NewStringUTF("[-] GAGAL: Driver Sistem Menolak Dilepas!");
        }
    }

    // 2. MANTRA BANGUN (Set Alternate Setting 1)
    // Ini adalah kunci utama agar suara keluar. Kita set mode streaming aktif.
    struct usbdevfs_setinterface setintf;
    setintf.interface = interface_id;
    setintf.altsetting = 1; // CX31993 biasanya butuh mode 1 atau 2
    if (ioctl(fd, USBDEVFS_SETINTERFACE, &setintf) < 0) {
        return env->NewStringUTF("[-] GAGAL: DAC Menolak Pindah ke Mode Aktif!");
    }

    LOGI("[+] KERNEL BYPASS SUKSES! Memulai Injeksi Sine Wave...");

    // 3. GENERATE & TEMBAK AUDIO (Isochronous Simulation via Bulk)
    const int bufferSize = 384; // Sesuai Packet Size kamu
    unsigned char buffer[bufferSize];
    double phase = 0.0;
    double freq = 440.0;
    double sampleRate = 44100.0;

    // Loop pengiriman data (Kita tes selama 5 detik saja dulu)
    for (int loop = 0; loop < 5000; ++loop) {
        for (int i = 0; i < bufferSize; i += 2) {
            short sample = (short)(sin(phase) * 32767);
            buffer[i] = sample & 0xFF;
            buffer[i + 1] = (sample >> 8) & 0xFF;
            phase += 2.0 * M_PI * freq / sampleRate;
        }

        struct usbdevfs_bulktransfer bulk;
        bulk.ep = endpoint_addr;
        bulk.len = bufferSize;
        bulk.timeout = 1000;
        bulk.data = buffer;

        // Tembak langsung ke Kernel USB File System
        ioctl(fd, USBDEVFS_BULK, &bulk);
        usleep(1000); // Sinkronisasi manual 1ms
    }

    return env->NewStringUTF("[+] DONE: Injeksi Selesai. Apakah ada suara?");
}

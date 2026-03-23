#include <jni.h>
#include <string>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <unistd.h>
#include <math.h>
#include <android/log.h>

#define LOG_TAG "xAudio_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// MANUAL DEFINE karena NDK lama tidak punya ini
#ifndef USBDEVFS_DISCONNECTIF
struct usbdevfs_disconnectif {
    unsigned int interface;
    unsigned int flags;
};
#define USBDEVFS_DISCONNECTIF _IOR('U', 27, struct usbdevfs_disconnectif)
#endif

#ifndef USBDEVFS_DISCONNECT_IF_DRIVER_IF_BOUND
#define USBDEVFS_DISCONNECT_IF_DRIVER_IF_BOUND 0x01
#endif

extern "C" JNIEXPORT jstring JNICALL
Java_com_xaudio_MainActivity_startBypass(JNIEnv* env, jobject thiz, jint fd, jint interface_id, jint endpoint_addr) {
    
    // 1. Tendang Driver Kernel (Pakai definisi manual tadi)
    struct usbdevfs_disconnectif disc;
    disc.interface = (unsigned int)interface_id;
    disc.flags = USBDEVFS_DISCONNECT_IF_DRIVER_IF_BOUND;
    ioctl(fd, USBDEVFS_DISCONNECTIF, &disc);

    // 2. Klaim Interface
    if (ioctl(fd, USBDEVFS_CLAIMINTERFACE, &interface_id) < 0) {
        return env->NewStringUTF("[-] ERROR: Gagal Klaim Interface");
    }

    // 3. Mantra Bangun: Set Alternate Setting 1
    struct usbdevfs_setinterface setintf;
    setintf.interface = (unsigned int)interface_id;
    setintf.altsetting = 1;
    ioctl(fd, USBDEVFS_SETINTERFACE, &setintf);

    // 4. Injeksi Sine Wave (Audio Test)
    unsigned char buffer[384];
    double phase = 0.0;
    for (int i = 0; i < 3000; i++) {
        for (int j = 0; j < 384; j += 2) {
            short sample = (short)(sin(phase) * 30000);
            buffer[j] = sample & 0xFF;
            buffer[j+1] = (sample >> 8) & 0xFF;
            phase += 2.0 * 3.14159 * 440.0 / 44100.0;
        }

        struct usbdevfs_bulktransfer bulk;
        bulk.ep = (unsigned int)endpoint_addr;
        bulk.len = 384;
        bulk.timeout = 1000;
        bulk.data = buffer;

        ioctl(fd, USBDEVFS_BULK, &bulk);
        usleep(1000); 
    }

    return env->NewStringUTF("[+] BERHASIL: Injeksi Selesai!");
}

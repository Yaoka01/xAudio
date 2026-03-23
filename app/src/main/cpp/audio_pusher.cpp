#include <jni.h>
#include <string>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <android/log.h>
#include <math.h>
#include <vector>

#define LOG_TAG "xaudio_engine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_xaudio_MainActivity_startBypass(
        JNIEnv* env,
        jobject /* this */,
        jint fd,
        jint interface_id,
        jint endpoint_addr) {

    // 1. TENDANG DRIVER BAWAAN iQOO
    struct usbdevfs_disconnectif disc;
    disc.interface = interface_id;
    disc.flags = USBDEVFS_DISCONNECT_IF_DRIVER_IF_BOUND;
    ioctl(fd, USBDEVFS_DISCONNECTIF, &disc);

    // 2. KLAIM KENDALI PENUH
    if (ioctl(fd, USBDEVFS_CLAIMINTERFACE, &interface_id) < 0) {
        return env->NewStringUTF("[-] GAGAL: Interface terkunci sistem.");
    }

    // 3. SET MODE STREAMING (Alt Setting 1)
    struct usbdevfs_setinterface setintf;
    setintf.interface = interface_id;
    setintf.altsetting = 1; 
    ioctl(fd, USBDEVFS_SETINTERFACE, &setintf);

    LOGI("[+] ENGINE START: Mengirim Asynchronous Isochronous Stream...");

    // 4. SETUP URB (USB Request Block) - Cara UAPP
    // Kita siapkan 8 paket audio sekaligus agar tidak putus
    const int num_packets = 8;
    const int packet_size = 384; 
    unsigned char buffer[num_packets * packet_size];
    
    struct usbdevfs_urb urb;
    memset(&urb, 0, sizeof(urb));
    urb.type = USBDEVFS_URB_TYPE_ISO;
    urb.endpoint = endpoint_addr;
    urb.buffer = buffer;
    urb.buffer_length = sizeof(buffer);
    urb.number_of_packets = num_packets;

    // Isi detail tiap paket
    struct usbdevfs_iso_packet_desc packets[num_packets];
    for (int i = 0; i < num_packets; ++i) {
        packets[i].length = packet_size;
        urb.iso_frame_desc[i].length = packet_size;
    }

    // 5. TEMBAK! (Looping Sine Wave)
    double phase = 0.0;
    for (int loop = 0; loop < 2000; ++loop) {
        // Isi buffer dengan Sine Wave
        for (int i = 0; i < num_packets * packet_size; i += 2) {
            short sample = (short)(sin(phase) * 32767);
            buffer[i] = sample & 0xFF;
            buffer[i+1] = (sample >> 8) & 0xFF;
            phase += 2.0 * M_PI * 440.0 / 44100.0;
        }

        // Kirim paket secara asinkron (Direct to Kernel)
        if (ioctl(fd, USBDEVFS_SUBMITURB, &urb) < 0) {
            return env->NewStringUTF("[-] ERROR: Kernel menolak URB Audio.");
        }
        
        // Tunggu paket selesai dikirim
        struct usbdevfs_urb *reaped_urb;
        ioctl(fd, USBDEVFS_REAPURB, &reaped_urb);
        
        usleep(1000); // Sinkronisasi 1ms
    }

    return env->NewStringUTF("[+] BERHASIL! Jika masih hening, CX31993 butuh Sample Rate spesifik.");
}

#include "qemu/osdep.h"

#ifdef __ANDROID__
#include "ui/xemu-android-native.h"

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <math.h>
#include <stdarg.h>

#include "qemu/main-loop.h"
#include "system/runstate.h"
#include "qemu/thread.h"
#include "ui/xemu-settings.h"

static QemuMutex window_lock;
static ANativeWindow *android_window;
static uint32_t window_generation;
static bool lock_initialized;

#define ANDROID_AUDIO_CHANNELS 2
#define ANDROID_AUDIO_SAMPLE_RATE 48000
#define ANDROID_AUDIO_RING_FRAMES 8192

static QemuMutex audio_lock;
static bool audio_lock_initialized;
static AAudioStream *audio_stream;
static int16_t audio_ring[ANDROID_AUDIO_RING_FRAMES][ANDROID_AUDIO_CHANNELS];
static size_t audio_read_pos;
static size_t audio_write_pos;
static size_t audio_queued_frames;

static void ensure_lock(void)
{
    if (!lock_initialized) {
        qemu_mutex_init(&window_lock);
        lock_initialized = true;
    }
}

static void ensure_audio_lock(void)
{
    if (!audio_lock_initialized) {
        qemu_mutex_init(&audio_lock);
        audio_lock_initialized = true;
    }
}

void xemu_android_set_native_window(ANativeWindow *window)
{
    ensure_lock();
    qemu_mutex_lock(&window_lock);
    if (android_window) {
        ANativeWindow_release(android_window);
    }
    android_window = window;
    if (android_window) {
        ANativeWindow_acquire(android_window);
    }
    window_generation++;
    qemu_mutex_unlock(&window_lock);
}

ANativeWindow *xemu_android_acquire_native_window(void)
{
    ANativeWindow *window;

    ensure_lock();
    qemu_mutex_lock(&window_lock);
    window = android_window;
    if (window) {
        ANativeWindow_acquire(window);
    }
    qemu_mutex_unlock(&window_lock);

    return window;
}

void xemu_android_release_native_window(ANativeWindow *window)
{
    if (window) {
        ANativeWindow_release(window);
    }
}

uint32_t xemu_android_get_native_window_generation(void)
{
    uint32_t generation;

    ensure_lock();
    qemu_mutex_lock(&window_lock);
    generation = window_generation;
    qemu_mutex_unlock(&window_lock);

    return generation;
}

static void xemu_android_vlog(int prio, const char *fmt, va_list ap)
{
    __android_log_vprint(prio, "X-OG Mobile", fmt, ap);
}

void xemu_android_log_info(const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    xemu_android_vlog(ANDROID_LOG_INFO, fmt, ap);
    va_end(ap);
}

void xemu_android_log_warn(const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    xemu_android_vlog(ANDROID_LOG_WARN, fmt, ap);
    va_end(ap);
}

static void reset_audio_ring_locked(void)
{
    audio_read_pos = 0;
    audio_write_pos = 0;
    audio_queued_frames = 0;
}

static aaudio_data_callback_result_t xemu_android_audio_data_callback(
    AAudioStream *stream, void *user_data, void *audio_data, int32_t num_frames)
{
    int16_t *out = audio_data;

    (void)stream;
    (void)user_data;

    ensure_audio_lock();
    qemu_mutex_lock(&audio_lock);
    for (int32_t i = 0; i < num_frames; i++) {
        if (audio_queued_frames > 0) {
            out[i * 2] = audio_ring[audio_read_pos][0];
            out[i * 2 + 1] = audio_ring[audio_read_pos][1];
            audio_read_pos = (audio_read_pos + 1) % ANDROID_AUDIO_RING_FRAMES;
            audio_queued_frames--;
        } else {
            out[i * 2] = 0;
            out[i * 2 + 1] = 0;
        }
    }
    qemu_mutex_unlock(&audio_lock);

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

bool xemu_android_audio_init(void)
{
    AAudioStreamBuilder *builder = NULL;
    AAudioStream *stream = NULL;
    aaudio_result_t result;

    ensure_audio_lock();
    qemu_mutex_lock(&audio_lock);
    if (audio_stream) {
        qemu_mutex_unlock(&audio_lock);
        return true;
    }
    reset_audio_ring_locked();
    qemu_mutex_unlock(&audio_lock);

    result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK || !builder) {
        xemu_android_log_warn("AAudio builder unavailable: %s",
                              AAudio_convertResultToText(result));
        return false;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setPerformanceMode(builder,
                                           AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, ANDROID_AUDIO_CHANNELS);
    AAudioStreamBuilder_setSampleRate(builder, ANDROID_AUDIO_SAMPLE_RATE);
    AAudioStreamBuilder_setFramesPerDataCallback(builder, 256);
    AAudioStreamBuilder_setDataCallback(builder,
                                        xemu_android_audio_data_callback, NULL);

    result = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK || !stream) {
        xemu_android_log_warn("AAudio open failed: %s",
                              AAudio_convertResultToText(result));
        return false;
    }

    qemu_mutex_lock(&audio_lock);
    audio_stream = stream;
    qemu_mutex_unlock(&audio_lock);

    result = AAudioStream_requestStart(stream);
    if (result != AAUDIO_OK) {
        xemu_android_log_warn("AAudio start failed: %s",
                              AAudio_convertResultToText(result));
        xemu_android_audio_shutdown();
        return false;
    }

    xemu_android_log_info("Android AAudio output started: %d Hz, %d channels",
                          AAudioStream_getSampleRate(stream),
                          AAudioStream_getChannelCount(stream));
    return true;
}

void xemu_android_audio_shutdown(void)
{
    AAudioStream *stream;

    ensure_audio_lock();
    qemu_mutex_lock(&audio_lock);
    stream = audio_stream;
    audio_stream = NULL;
    reset_audio_ring_locked();
    qemu_mutex_unlock(&audio_lock);

    if (stream) {
        AAudioStream_requestStop(stream);
        AAudioStream_close(stream);
        xemu_android_log_info("Android AAudio output stopped");
    }
}

void xemu_android_audio_queue(const int16_t *samples, size_t frames)
{
    if (!samples || frames == 0) {
        return;
    }

    ensure_audio_lock();
    qemu_mutex_lock(&audio_lock);
    if (!audio_stream) {
        qemu_mutex_unlock(&audio_lock);
        return;
    }

    for (size_t i = 0; i < frames; i++) {
        if (audio_queued_frames == ANDROID_AUDIO_RING_FRAMES) {
            audio_read_pos = (audio_read_pos + 1) % ANDROID_AUDIO_RING_FRAMES;
            audio_queued_frames--;
        }
        audio_ring[audio_write_pos][0] = samples[i * 2];
        audio_ring[audio_write_pos][1] = samples[i * 2 + 1];
        audio_write_pos = (audio_write_pos + 1) % ANDROID_AUDIO_RING_FRAMES;
        audio_queued_frames++;
    }
    qemu_mutex_unlock(&audio_lock);
}

int xemu_android_audio_queued_bytes(void)
{
    int queued;

    ensure_audio_lock();
    qemu_mutex_lock(&audio_lock);
    if (!audio_stream) {
        queued = -1;
    } else {
        queued = audio_queued_frames * ANDROID_AUDIO_CHANNELS *
                 sizeof(audio_ring[0][0]);
    }
    qemu_mutex_unlock(&audio_lock);

    return queued;
}

void xemu_android_audio_set_volume(float volume)
{
    g_config.audio.volume_limit = fmaxf(0.0f, fminf(1.0f, volume));
    xemu_android_log_info("Android audio volume set to %.2f",
                          g_config.audio.volume_limit);
}

void xemu_android_request_shutdown(void)
{
    xemu_android_log_info("Android requested orderly core shutdown");
    BQL_LOCK_GUARD();
    qemu_system_shutdown_request(SHUTDOWN_CAUSE_HOST_UI);
}

void xemu_android_request_pause(void)
{
    xemu_android_log_info("Android requested VM pause");
    BQL_LOCK_GUARD();
    qemu_system_vmstop_request_prepare();
    qemu_system_vmstop_request(RUN_STATE_PAUSED);
}

void xemu_android_request_resume(void)
{
    BQL_LOCK_GUARD();
    if (runstate_check(RUN_STATE_PAUSED)) {
        xemu_android_log_info("Android requested VM resume");
        vm_start();
    } else {
        xemu_android_log_info("Android resume ignored: VM is not paused");
    }
}
#endif

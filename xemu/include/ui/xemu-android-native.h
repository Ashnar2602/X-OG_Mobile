#ifndef UI_XEMU_ANDROID_NATIVE_H
#define UI_XEMU_ANDROID_NATIVE_H

#ifdef __ANDROID__
#include <android/native_window.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void xemu_android_set_native_window(ANativeWindow *window);
ANativeWindow *xemu_android_acquire_native_window(void);
void xemu_android_release_native_window(ANativeWindow *window);
uint32_t xemu_android_get_native_window_generation(void);
void xemu_android_log_info(const char *fmt, ...);
void xemu_android_log_warn(const char *fmt, ...);
void xemu_android_register_display_listener(void);
void xemu_android_input_set_button(int button, bool pressed);
void xemu_android_input_set_axis(int axis, float value);
void xemu_android_input_reset(void);
bool xemu_android_audio_init(void);
void xemu_android_audio_shutdown(void);
void xemu_android_audio_queue(const int16_t *samples, size_t frames);
int xemu_android_audio_queued_bytes(void);
void xemu_android_audio_set_volume(float volume);
void xemu_android_request_shutdown(void);
void xemu_android_request_pause(void);
void xemu_android_request_resume(void);

#ifdef __cplusplus
}
#endif
#endif

#endif

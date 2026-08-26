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

/*
 * Frame counters for the on-screen "FPS: produced / shown" overlay.
 * "Produced" increments once per guest flip-stall (a real frame the game
 * finished rendering); "presented" increments once per successful
 * vkQueuePresentKHR (a frame actually pushed to the display swapchain).
 * Comparing their rates tells apart a guest-side stall from the host
 * dropping frames it already had ready.
 */
void xemu_android_mark_frame_produced(void);
void xemu_android_mark_frame_presented(void);
void xemu_android_get_frame_stats(uint64_t *produced, uint64_t *presented);

/*
 * Diagnostic-only: logs QEMU runstate/vmstop/clock state to logcat, tagged
 * with `reason`. Safe to call from any thread; never has side effects.
 */
void xemu_android_log_watchdog_snapshot(const char *reason);

#ifdef __cplusplus
}
#endif
#endif

#endif

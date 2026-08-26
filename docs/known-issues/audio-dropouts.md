# Intermittent audio dropouts (Android)

**Status:** Open — root cause not identified. Investigation paused.

## Symptom

Audio cuts in and out intermittently during gameplay ("audio va e viene").
Video rendering does not visibly freeze or stutter while this happens.

## Known affected games

- Halo 2

## Test environment

- Device: Adreno 750 (arm64-v8a)
- Renderer: Android Vulkan backend
- Build: in-process core (`libxemu-core-i386.so`), AAudio output at 48000 Hz / 2 channels

## Facts gathered

- The dropouts were first observed after applying upstream sync "tranche 1"
  (commit `eed07116`, upstream range `92407546..fc13b780`), which applies two
  audio-related commits:
  - `bff2145f` — `NV_PAPU_XGSCNT` now returns real APU sample progress
    (`d->ep_frame_div * NUM_SAMPLES_PER_FRAME`) instead of a wall-clock-derived
    value.
  - `fc13b780` — `audio.use_dsp_jit` default changed from `true` to `false` in
    `config_spec.yml`.
- On this Android build, `hw/xbox/mcpx/apu/dsp/meson.build` compiles the DSP
  with `-DXEMU_DISABLE_DSP_JIT=1` whenever `host_os == 'android'`, so the DSP
  JIT is never built into the core and `dsp_set_engine()` always runs the
  interpreter regardless of `use_dsp_jit`. The `fc13b780` config default change
  has no effect on Android builds.
- An A/B test was performed: reverting `bff2145f` alone and rebuilding
  reproduced the identical dropouts. The dropouts are therefore not caused by
  either commit in tranche 1; they pre-exist in the Android port.
- The Android audio path (`xemu/ui/xemu-android-native.c`) queues PCM samples
  from the APU producer thread into a ring buffer, drained by an AAudio
  real-time data callback (`AAUDIO_PERFORMANCE_MODE_LOW_LATENCY`,
  256 frames per callback, ~5.3 ms period).
- Two changes were made to this path during investigation and tested on
  device; neither changed the symptom (dropouts remained identical):
  1. Widened the ring's low/high watermarks in
     `mcpx_apu_monitor_init()` (`xemu/hw/xbox/mcpx/apu/monitor.c`) from
     ~5 ms / ~16 ms to 30 ms / 90 ms.
  2. Replaced the mutex-guarded ring buffer in
     `xemu/ui/xemu-android-native.c` with a lock-free single-producer/
     single-consumer ring using atomic cursors, removing the shared
     `pthread_mutex` between the APU producer thread and the AAudio
     callback thread.
- Both changes remain in the codebase (they are not known to be harmful,
  just not the fix).
- An on-screen diagnostic overlay was added (default on, top-left corner):
  `FPS: <produced> / <shown>`.
  - *produced* = rate of guest flip-stalls
    (`xemu_android_mark_frame_produced()`, called from
    `pgraph_vk_flip_stall()` in `xemu/hw/xbox/nv2a/pgraph/vk/renderer.c`).
  - *shown* = rate of successful `vkQueuePresentKHR` calls
    (`xemu_android_mark_frame_presented()`, called from
    `pgraph_vk_android_present()` in `xemu/hw/xbox/nv2a/pgraph/vk/display.c`).
  - Counters are exposed to Java via `NativeBridge.nativeGetFrameStats()`.
  - This overlay has been installed but its readings during an audio dropout
    have not yet been recorded/analyzed.

## To investigate

- Correlate the FPS overlay readings (produced vs. shown) with an audio
  dropout in Halo 2 to determine whether dropouts coincide with a guest-side
  emulation stall (both numbers drop together) or are isolated to the audio
  path (FPS stays stable while audio cuts out).

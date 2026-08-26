# Emulation freeze after VM pause/resume

**Status:** Open — root cause not identified. Diagnostic instrumentation added,
watching for a reproduction with hard evidence.

## Symptom

The game freezes indefinitely: the last video frame stays on screen, audio
repeats the same ~1 second of content on a loop, and the app never recovers
on its own. The process does not crash (no ANDROID FATAL/AndroidRuntime
exception, no process restart) and Android's own compositor keeps ticking
normally the whole time; only the guest (the emulated Xbox) stops making
progress.

## Facts gathered (2026-08-26 occurrence)

- Timeline (single continuous process, PID unchanged throughout):
  - App launched, rendering normally at ~30 fps.
  - Last real frame rendered ~2m42s after launch.
  - 5 seconds later: `xemu_android_request_pause()` logs "Android requested
    VM pause" (`xemu/ui/xemu-android-native.c`).
  - ~56 seconds after that: `xemu_android_request_resume()` logs "Android
    requested VM resume" — the only code path that can produce this log line
    is a tap on the pause menu's "Resume Game" button
    (`MainActivity.requestResume()` → `NativeBridge.nativeResume()`); no
    other caller exists (audited every call site).
  - From the resume request onward, checked repeatedly over 7+ minutes: zero
    new "Vulkan render/flip/present" log lines, zero "gfx switch/update"
    (guest display) log lines. Never recovered on its own.
- `auto_pause_ms` (the settings-configurable idle-auto-pause timer) was `0`
  ("Off") in the persisted preferences at the time — `scheduleIdleAutoPause()`
  only schedules when `delayMs > 0`, so the idle timer is structurally ruled
  out as the trigger for this occurrence.
- The user did not knowingly interact with any pause control. The most
  plausible trigger is an accidental touch on the on-screen Pause button
  (bottom-center of the touch controller, a natural thumb-rest position in a
  two-handed landscape grip) followed by an equally accidental tap landing on
  "Resume Game" in the pause overlay that appeared — this is speculation, not
  confirmed.
- Per-thread CPU accounting (`/proc/<pid>/task/*/stat`, sampled 3s apart,
  repeated 4 minutes later with an identical pattern): one thread sustained
  ~55-60% of a core, eight more threads sustained ~20-30% each (matching the
  device's 8 cores), while a supposedly-idle game produced zero output. This
  is a livelock, not a blocked/parked state: real CPU is being burned
  continuously with no forward progress, and it does not self-recover.
- Ruled out:
  - Vulkan surface loss: no `surfaceDestroyed`/`surfaceCreated` lifecycle
    events occurred; the `ANativeWindow` was never recreated.
  - A native crash: no SIGSEGV/SIGABRT/FATAL/tombstone in logcat.
  - The APU (audio) thread being the stuck one: `mcpx_apu_frame_thread`
    (`xemu/hw/xbox/mcpx/apu/apu.c`) correctly parks on a condvar when
    `pause_requested` is set (via `mcpx_apu_wait_for_idle`/`mcpx_apu_resume`,
    the standard QEMU device vmstate-change hook) — the audio thread showed
    near-zero CPU, consistent with either being correctly parked or with the
    APU thread being awake but faithfully replaying unchanging register
    state driven by something else (the real stuck thread) upstream of it.
  - None of our own Xbox device emulation code (nv2a, apu) touches QEMU
    runstate directly — the pause/resume mechanism is exclusively the
    standard `qemu_system_vmstop_request()` / `vm_start()` QEMU API, called
    only from `xemu_android_request_pause/resume()`
    (`xemu/ui/xemu-android-native.c`).

## Leading hypothesis (unconfirmed)

A QEMU timer/clock catch-up issue after a real-world pause of non-trivial
length (~55s here): `QEMU_CLOCK_VIRTUAL` is expected to stay frozen for the
guest during a pause and resume smoothly; if some subsystem's timer-driven
loop doesn't handle a large backlog of "should have already fired" callbacks
correctly on resume, it could busy-loop instead of advancing normally. This
would explain sustained CPU with zero progress and repeating audio content
(the APU replaying unchanging upstream state) without requiring a bug in our
own pause/resume glue, which reads as correct on inspection.

This is *not confirmed* — there's no stack trace or debugger evidence behind
it, only elimination of the alternatives reachable from logcat alone.

## Instrumentation added

To get real evidence instead of guessing further, `xemu_android_request_pause`
and `xemu_android_request_resume` now log a state snapshot
(`xemu_android_log_watchdog_snapshot()` in `xemu-android-native.c`): QEMU
runstate, whether a vmstop is still pending, and both `QEMU_CLOCK_VIRTUAL`
and `QEMU_CLOCK_REALTIME` in ms. It reads QEMU globals without the BQL
(deliberately — the whole point is to stay safe to call even if something
else is stuck holding it) and never has side effects.

`MainActivity.checkFrameWatchdog()` runs inside the existing 500ms FPS
poller: if the produced-frame counter stops advancing for 3s while the app
does *not* think it's paused (`!emulationPaused`), it calls
`NativeBridge.nativeLogWatchdogSnapshot()` and re-logs every 5s while the
stall continues, so a future occurrence leaves a paper trail: virtual-clock
behavior right at the moment of the freeze, and confirmation of whether
`vmstop_pending` ever gets stuck.

## To investigate next

- Reproduce and capture the new watchdog log output — does
  `QEMU_CLOCK_VIRTUAL` advance at all after resume, or is it also stuck?
  Does `vmstop_pending` ever show true when it shouldn't?
- If reproducible on demand, consider whether it correlates with pause
  duration (does a 5s pause recover fine but a 60s pause not?).
- Confirm or rule out the accidental-touch trigger theory: the Pause button
  sits at the bottom-center margin, a plausible thumb-rest spot during
  two-handed landscape play.

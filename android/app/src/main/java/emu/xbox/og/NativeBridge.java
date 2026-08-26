package emu.xbox.og;

import android.view.Surface;

final class NativeBridge {
    static {
        System.loadLibrary("xboxemu_android");
    }

    private NativeBridge() {
    }

    static native String nativeInit(String filesDir, String cacheDir, String nativeLibDir);
    static native void nativeSurfaceCreated(Surface surface, int width, int height);
    static native void nativeSurfaceChanged(Surface surface, int width, int height);
    static native void nativeSurfaceDestroyed();
    static native String nativeLaunch(String mcpx, String bios, String hdd, String disc,
                                      String renderer, int audioVolume, boolean audioMuted,
                                      boolean skipBootAnimation, String avpack, int discFd);
    static native void nativeSetButton(int button, boolean pressed);
    static native void nativeSetAxis(int axis, float value);
    static native void nativeSetAudio(int volume, boolean muted);
    static native String nativePause();
    static native String nativeResume();
    static native String nativeStop(int timeoutMs);
    static native boolean nativeIsRunning();
    // [produced, presented] cumulative counts; see xemu_android_get_frame_stats.
    static native long[] nativeGetFrameStats();
    // Diagnostic-only: logs QEMU runstate/vmstop/clock state to logcat.
    static native void nativeLogWatchdogSnapshot(String reason);

    // TODO(savestate): expose nativeSaveState/nativeLoadState/nativeListStates once
    // the Android UI has a real snapshot manager instead of placeholder buttons.
}

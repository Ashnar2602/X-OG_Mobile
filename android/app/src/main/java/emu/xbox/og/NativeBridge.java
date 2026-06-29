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
    static native String nativeLaunch(String mcpx, String bios, String hdd, int discFd,
                                      String renderer, int audioVolume, boolean audioMuted,
                                      boolean skipBootAnimation, String avpack);
    static native void nativeSetButton(int button, boolean pressed);
    static native void nativeSetAxis(int axis, float value);
    static native void nativeSetAudio(int volume, boolean muted);
    static native String nativePause();
    static native String nativeResume();
    static native String nativeStop(int timeoutMs);
    static native boolean nativeIsRunning();

    // TODO(savestate): expose nativeSaveState/nativeLoadState/nativeListStates once
    // the Android UI has a real snapshot manager instead of placeholder buttons.
}

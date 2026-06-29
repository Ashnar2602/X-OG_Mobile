#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <vulkan/vulkan.h>

#include <errno.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#include <chrono>
#include <condition_variable>
#include <array>
#include <atomic>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#define LOG_TAG "X-OG Mobile"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

extern "C" int xemu_android_main(int argc, char **argv);
extern "C" void xemu_android_set_native_window(ANativeWindow *window);
extern "C" void xemu_android_input_set_button(int button, bool pressed);
extern "C" void xemu_android_input_set_axis(int axis, float value);
extern "C" void xemu_android_input_reset(void);
extern "C" void xemu_android_audio_set_volume(float volume);
extern "C" void xemu_android_request_shutdown(void);
extern "C" void xemu_android_request_pause(void);
extern "C" void xemu_android_request_resume(void);

namespace {
std::mutex g_lock;
std::condition_variable g_core_cv;
ANativeWindow *g_window = nullptr;
EGLDisplay g_display = EGL_NO_DISPLAY;
EGLContext g_context = EGL_NO_CONTEXT;
EGLSurface g_surface = EGL_NO_SURFACE;
bool g_vulkan_available = false;
std::array<bool, 16> g_buttons{};
std::array<float, 8> g_axes{};
std::string g_files_dir;
std::string g_cache_dir;
std::string g_native_lib_dir;
pid_t g_xemu_pid = -1;
std::thread g_xemu_thread;
std::atomic<bool> g_xemu_running{false};
bool g_stdio_redirected = false;

std::string jstr(JNIEnv *env, jstring value) {
    if (!value) {
        return {};
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars ? chars : "";
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

jstring ret(JNIEnv *env, const std::string &value) {
    return env->NewStringUTF(value.c_str());
}

void destroy_gl_locked() {
    if (g_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (g_surface != EGL_NO_SURFACE) {
            eglDestroySurface(g_display, g_surface);
        }
        if (g_context != EGL_NO_CONTEXT) {
            eglDestroyContext(g_display, g_context);
        }
        eglTerminate(g_display);
    }
    g_display = EGL_NO_DISPLAY;
    g_context = EGL_NO_CONTEXT;
    g_surface = EGL_NO_SURFACE;
}

bool init_gl_locked() {
    if (!g_window) {
        return false;
    }
    destroy_gl_locked();
    g_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g_display == EGL_NO_DISPLAY || !eglInitialize(g_display, nullptr, nullptr)) {
        LOGW("eglInitialize failed");
        return false;
    }

    const EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24,
        EGL_STENCIL_SIZE, 8,
        EGL_NONE
    };
    EGLConfig config = nullptr;
    EGLint count = 0;
    if (!eglChooseConfig(g_display, attribs, &config, 1, &count) || count == 0) {
        LOGW("eglChooseConfig failed");
        return false;
    }
    const EGLint ctx_attribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE
    };
    g_context = eglCreateContext(g_display, config, EGL_NO_CONTEXT, ctx_attribs);
    g_surface = eglCreateWindowSurface(g_display, config, g_window, nullptr);
    if (g_context == EGL_NO_CONTEXT || g_surface == EGL_NO_SURFACE) {
        LOGW("EGL context/surface creation failed");
        return false;
    }
    if (!eglMakeCurrent(g_display, g_surface, g_surface, g_context)) {
        LOGW("eglMakeCurrent failed");
        return false;
    }
    glClearColor(0.02f, 0.04f, 0.05f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    eglSwapBuffers(g_display, g_surface);
    return true;
}

bool probe_vulkan() {
    uint32_t version = 0;
    if (vkEnumerateInstanceVersion(&version) == VK_SUCCESS) {
        return VK_VERSION_MAJOR(version) >= 1;
    }
    return false;
}

void start_stdio_logcat_locked() {
    if (g_stdio_redirected) {
        return;
    }

    int pipe_fds[2] = {-1, -1};
    if (pipe(pipe_fds) != 0) {
        LOGW("Unable to create stdout/stderr logcat pipe: %s", strerror(errno));
        return;
    }

    dup2(pipe_fds[1], STDOUT_FILENO);
    dup2(pipe_fds[1], STDERR_FILENO);
    close(pipe_fds[1]);
    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);

    std::thread([read_fd = pipe_fds[0]]() {
        char buffer[512];
        std::string line;
        for (;;) {
            ssize_t count = read(read_fd, buffer, sizeof(buffer));
            if (count <= 0) {
                break;
            }
            for (ssize_t i = 0; i < count; ++i) {
                if (buffer[i] == '\n') {
                    if (!line.empty()) {
                        LOGI("xemu: %s", line.c_str());
                        line.clear();
                    }
                } else {
                    line.push_back(buffer[i]);
                    if (line.size() >= 480) {
                        LOGI("xemu: %s", line.c_str());
                        line.clear();
                    }
                }
            }
        }
        close(read_fd);
    }).detach();

    g_stdio_redirected = true;
}

void ensure_dir(const std::string &path) {
    if (!path.empty()) {
        mkdir(path.c_str(), 0700);
    }
}

std::string drive_arg(int index, const std::string &media, const std::string &path) {
    return "index=" + std::to_string(index) + ",media=" + media + ",file=" + path + ",format=raw";
}

void xemu_thread_main(std::vector<std::string> args) {
    std::vector<char *> argv;
    argv.reserve(args.size() + 1);
    for (std::string &arg : args) {
        argv.push_back(arg.data());
    }
    argv.push_back(nullptr);

    LOGI("Starting in-process xemu core");
    int status = xemu_android_main(static_cast<int>(args.size()), argv.data());
    LOGI("In-process xemu core exited with status %d", status);
    {
        std::lock_guard<std::mutex> guard(g_lock);
        g_xemu_running.store(false);
    }
    g_core_cv.notify_all();
}

void join_finished_core_locked(std::unique_lock<std::mutex> &guard) {
    if (!g_xemu_running.load() && g_xemu_thread.joinable()) {
        std::thread finished = std::move(g_xemu_thread);
        guard.unlock();
        finished.join();
        LOGI("Joined finished xemu core thread");
        guard.lock();
    }
}

float audio_volume_to_float(int volume, bool muted) {
    if (muted) {
        return 0.0f;
    }
    if (volume < 0) {
        volume = 0;
    } else if (volume > 100) {
        volume = 100;
    }
    return static_cast<float>(volume) / 100.0f;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_emu_xbox_og_NativeBridge_nativeInit(JNIEnv *env, jclass, jstring files_dir,
                                         jstring cache_dir, jstring native_lib_dir) {
    std::lock_guard<std::mutex> guard(g_lock);
    g_files_dir = jstr(env, files_dir);
    g_cache_dir = jstr(env, cache_dir);
    g_native_lib_dir = jstr(env, native_lib_dir);
    g_vulkan_available = probe_vulkan();
    start_stdio_logcat_locked();
    std::ostringstream out;
    out << "Native backend ready. Vulkan probe: "
        << (g_vulkan_available ? "available" : "unavailable")
        << ". SDL runtime: disabled. xemu core library: "
        << g_native_lib_dir << "/libxemu-core-i386.so";
    return ret(env, out.str());
}

extern "C" JNIEXPORT void JNICALL
Java_emu_xbox_og_NativeBridge_nativeSurfaceCreated(JNIEnv *env, jclass, jobject surface, jint width, jint height) {
    std::lock_guard<std::mutex> guard(g_lock);
    if (g_window) {
        ANativeWindow_release(g_window);
    }
    g_window = ANativeWindow_fromSurface(env, surface);
    if (g_window) {
        ANativeWindow_setBuffersGeometry(g_window, width, height, WINDOW_FORMAT_RGBA_8888);
        xemu_android_set_native_window(g_window);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_emu_xbox_og_NativeBridge_nativeSurfaceChanged(JNIEnv *env, jclass, jobject surface, jint width, jint height) {
    Java_emu_xbox_og_NativeBridge_nativeSurfaceCreated(env, nullptr, surface, width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_emu_xbox_og_NativeBridge_nativeSurfaceDestroyed(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> guard(g_lock);
    destroy_gl_locked();
    if (g_window) {
        xemu_android_set_native_window(nullptr);
        ANativeWindow_release(g_window);
        g_window = nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_emu_xbox_og_NativeBridge_nativeLaunch(JNIEnv *env, jclass, jstring mcpx, jstring bios,
                                           jstring hdd, jint disc_fd, jstring renderer,
                                           jint audio_volume, jboolean audio_muted,
                                           jboolean skip_boot_animation, jstring avpack) {
    std::unique_lock<std::mutex> guard(g_lock);
    join_finished_core_locked(guard);

    const std::string mcpx_path = jstr(env, mcpx);
    const std::string bios_path = jstr(env, bios);
    const std::string hdd_path = jstr(env, hdd);
    const std::string disc_path = disc_fd >= 0
            ? "androidfd:" + std::to_string(static_cast<int>(disc_fd))
            : "";
    const std::string renderer_value = jstr(env, renderer);
    const std::string avpack_value = jstr(env, avpack);

    if (mcpx_path.empty() || bios_path.empty() || hdd_path.empty()) {
        return ret(env, "Missing required file. MCPX, BIOS and HDD are required before launch.");
    }
    if (g_xemu_running.load()) {
        return ret(env, "xemu core is already running in-process");
    }

    const std::string executable = g_native_lib_dir + "/libxemu-core-i386.so";
    const std::string config_dir = g_files_dir + "/xemu-config";
    const std::string data_dir = g_files_dir + "/xemu-data";
    ensure_dir(config_dir);
    ensure_dir(data_dir);

    std::vector<std::string> args;
    args.push_back(executable);
    args.push_back("-monitor");
    args.push_back("none");
    args.push_back("-serial");
    args.push_back("none");
    args.push_back("-parallel");
    args.push_back("none");
    args.push_back("-accel");
    args.push_back("tcg");

    setenv("HOME", g_files_dir.c_str(), 1);
    setenv("XDG_CONFIG_HOME", config_dir.c_str(), 1);
    setenv("XDG_DATA_HOME", data_dir.c_str(), 1);
    setenv("LD_LIBRARY_PATH", g_native_lib_dir.c_str(), 1);
    setenv("XEMU_ANDROID_MCPX", mcpx_path.c_str(), 1);
    setenv("XEMU_ANDROID_BIOS", bios_path.c_str(), 1);
    setenv("XEMU_ANDROID_HDD", hdd_path.c_str(), 1);
    setenv("XEMU_ANDROID_DISC", disc_path.c_str(), 1);
    setenv("XEMU_ANDROID_RENDERER", renderer_value.c_str(), 1);
    setenv("XEMU_ANDROID_AUDIO_VOLUME",
           std::to_string(audio_volume_to_float(audio_volume, false)).c_str(), 1);
    setenv("XEMU_ANDROID_AUDIO_MUTED", audio_muted == JNI_TRUE ? "1" : "0", 1);
    setenv("XEMU_ANDROID_SKIP_BOOT_ANIM",
           skip_boot_animation == JNI_TRUE ? "1" : "0", 1);
    setenv("XEMU_ANDROID_AVPACK", avpack_value.c_str(), 1);
    chdir(g_files_dir.c_str());

    xemu_android_input_reset();
    g_xemu_running.store(true);
    g_xemu_thread = std::thread(xemu_thread_main, args);

    std::ostringstream out;
    out << "xemu core launched:"
        << "\n  mode=in-process native thread"
        << "\n  mcpx=" << (mcpx_path.empty() ? "missing" : mcpx_path)
        << "\n  bios=" << (bios_path.empty() ? "missing" : bios_path)
        << "\n  hdd=" << (hdd_path.empty() ? "missing" : hdd_path)
        << "\n  eeprom=" << config_dir << "/eeprom.bin (xemu generated/default)"
        << "\n  disc=" << (disc_path.empty() ? "missing" : disc_path)
        << "\nRenderer setting: " << renderer_value
        << "\nAudio volume: " << audio_volume << (audio_muted == JNI_TRUE ? " (muted)" : "")
        << "\nAV pack: " << avpack_value
        << "\nSkip boot animation: " << (skip_boot_animation == JNI_TRUE ? "yes" : "no")
        << "\nRenderer path: " << (g_vulkan_available ? "Vulkan preferred, GLES fallback initialized" : "GLES fallback initialized")
        << "\nCurrent core renderer: Android Vulkan presenter registered inside xemu.";
    LOGI("%s", out.str().c_str());
    return ret(env, out.str());
}

extern "C" JNIEXPORT void JNICALL
Java_emu_xbox_og_NativeBridge_nativeSetButton(JNIEnv *, jclass, jint button, jboolean pressed) {
    if (button >= 0 && button < static_cast<jint>(g_buttons.size())) {
        g_buttons[button] = pressed == JNI_TRUE;
        xemu_android_input_set_button(button, pressed == JNI_TRUE);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_emu_xbox_og_NativeBridge_nativeSetAxis(JNIEnv *, jclass, jint axis, jfloat value) {
    if (axis >= 0 && axis < static_cast<jint>(g_axes.size())) {
        g_axes[axis] = value;
        xemu_android_input_set_axis(axis, value);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_emu_xbox_og_NativeBridge_nativeSetAudio(JNIEnv *, jclass, jint volume, jboolean muted) {
    if (g_xemu_running.load()) {
        xemu_android_audio_set_volume(audio_volume_to_float(volume, muted == JNI_TRUE));
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_emu_xbox_og_NativeBridge_nativePause(JNIEnv *env, jclass) {
    if (!g_xemu_running.load()) {
        return ret(env, "xemu core is not running");
    }
    xemu_android_request_pause();
    return ret(env, "Pause requested");
}

extern "C" JNIEXPORT jstring JNICALL
Java_emu_xbox_og_NativeBridge_nativeResume(JNIEnv *env, jclass) {
    if (!g_xemu_running.load()) {
        return ret(env, "xemu core is not running");
    }
    xemu_android_request_resume();
    return ret(env, "Resume requested");
}

extern "C" JNIEXPORT jstring JNICALL
Java_emu_xbox_og_NativeBridge_nativeStop(JNIEnv *env, jclass, jint timeout_ms) {
    std::unique_lock<std::mutex> guard(g_lock);
    join_finished_core_locked(guard);

    bool requested = false;
    bool stopped = !g_xemu_running.load();
    if (!stopped) {
        LOGI("nativeStop requested; asking xemu core to shut down");
        xemu_android_request_shutdown();
        requested = true;
        stopped = g_core_cv.wait_for(
            guard,
            std::chrono::milliseconds(timeout_ms > 0 ? timeout_ms : 3000),
            [] { return !g_xemu_running.load(); });
    }

    xemu_android_input_reset();
    destroy_gl_locked();
    if (g_window) {
        xemu_android_set_native_window(nullptr);
        ANativeWindow_release(g_window);
        g_window = nullptr;
    }

    std::thread finished;
    if (!g_xemu_running.load() && g_xemu_thread.joinable()) {
        finished = std::move(g_xemu_thread);
    }
    guard.unlock();
    if (finished.joinable()) {
        finished.join();
        LOGI("Joined xemu core thread after stop");
    }

    if (stopped) {
        return ret(env, requested ? "xemu core stopped cleanly" : "xemu core was not running");
    }
    LOGW("xemu core shutdown still pending after timeout");
    return ret(env, "xemu core shutdown pending after timeout");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_emu_xbox_og_NativeBridge_nativeIsRunning(JNIEnv *, jclass) {
    return g_xemu_running.load() ? JNI_TRUE : JNI_FALSE;
}

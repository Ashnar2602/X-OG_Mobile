#include "qemu/osdep.h"

#ifdef __ANDROID__
#include "hw/xbox/nv2a/nv2a_int.h"
#include "ui/xemu-android-native.h"

#include <EGL/egl.h>
#include <GLES3/gl3.h>

typedef struct PGRAPHAndroidState {
    ANativeWindow *window;
    EGLDisplay display;
    EGLContext context;
    EGLSurface surface;
    int frame;
    int flushes;
    int surface_updates;
} PGRAPHAndroidState;

static void android_destroy(PGRAPHAndroidState *r)
{
    if (!r) {
        return;
    }
    if (r->display != EGL_NO_DISPLAY) {
        eglMakeCurrent(r->display, EGL_NO_SURFACE, EGL_NO_SURFACE,
                       EGL_NO_CONTEXT);
        if (r->surface != EGL_NO_SURFACE) {
            eglDestroySurface(r->display, r->surface);
        }
        if (r->context != EGL_NO_CONTEXT) {
            eglDestroyContext(r->display, r->context);
        }
        eglTerminate(r->display);
    }
    xemu_android_release_native_window(r->window);
    g_free(r);
}

static bool android_init_egl(PGRAPHAndroidState *r, Error **errp)
{
    static const EGLint config_attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24,
        EGL_STENCIL_SIZE, 8,
        EGL_NONE,
    };
    static const EGLint context_attribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE,
    };
    EGLConfig config = NULL;
    EGLint count = 0;

    r->window = xemu_android_acquire_native_window();
    if (!r->window) {
        error_setg(errp, "Android renderer has no ANativeWindow");
        return false;
    }

    r->display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (r->display == EGL_NO_DISPLAY || !eglInitialize(r->display, NULL, NULL)) {
        error_setg(errp, "eglInitialize failed");
        return false;
    }
    if (!eglChooseConfig(r->display, config_attribs, &config, 1, &count) ||
        count == 0) {
        error_setg(errp, "eglChooseConfig failed");
        return false;
    }

    r->context = eglCreateContext(r->display, config, EGL_NO_CONTEXT,
                                  context_attribs);
    r->surface = eglCreateWindowSurface(r->display, config, r->window, NULL);
    if (r->context == EGL_NO_CONTEXT || r->surface == EGL_NO_SURFACE) {
        error_setg(errp, "Android EGL context/surface creation failed");
        return false;
    }
    if (!eglMakeCurrent(r->display, r->surface, r->surface, r->context)) {
        error_setg(errp, "eglMakeCurrent failed");
        return false;
    }

    xemu_android_log_info("Android GLES presenter initialized");
    return true;
}

static void android_present(PGRAPHAndroidState *r, float phase)
{
    if (!r || r->display == EGL_NO_DISPLAY || r->surface == EGL_NO_SURFACE) {
        return;
    }

    eglMakeCurrent(r->display, r->surface, r->surface, r->context);
    glViewport(0, 0, ANativeWindow_getWidth(r->window),
               ANativeWindow_getHeight(r->window));
    glClearColor(0.02f + phase, 0.08f, 0.16f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    eglSwapBuffers(r->display, r->surface);
}

static void pgraph_android_init(NV2AState *d, Error **errp)
{
    PGRAPHAndroidState *r = g_malloc0(sizeof(*r));
    r->display = EGL_NO_DISPLAY;
    r->context = EGL_NO_CONTEXT;
    r->surface = EGL_NO_SURFACE;

    if (!android_init_egl(r, errp)) {
        android_destroy(r);
        return;
    }

    d->pgraph.gl_renderer_state = (PGRAPHGLState *)r;
    android_present(r, 0.0f);
}

static void pgraph_android_finalize(NV2AState *d)
{
    xemu_android_log_info("Android GLES presenter finalized");
    android_destroy((PGRAPHAndroidState *)d->pgraph.gl_renderer_state);
    d->pgraph.gl_renderer_state = NULL;
}

static void pgraph_android_sync(NV2AState *d)
{
    PGRAPHAndroidState *r = (PGRAPHAndroidState *)d->pgraph.gl_renderer_state;
    android_present(r, (r->frame++ & 31) / 256.0f);
    if ((r->frame % 60) == 1) {
        xemu_android_log_info("Android GLES presenter sync frame=%d", r->frame);
    }
    qatomic_set(&d->pgraph.sync_pending, false);
    qemu_event_set(&d->pgraph.sync_complete);
}

static void pgraph_android_flush(NV2AState *d)
{
    PGRAPHAndroidState *r = (PGRAPHAndroidState *)d->pgraph.gl_renderer_state;
    if (r && (r->flushes++ % 60) == 0) {
        xemu_android_log_info("Android GLES presenter flush count=%d", r->flushes);
    }
    qatomic_set(&d->pgraph.flush_pending, false);
    qemu_event_set(&d->pgraph.flush_complete);
}

static void pgraph_android_process_pending(NV2AState *d)
{
    if (qatomic_read(&d->pgraph.sync_pending) ||
        qatomic_read(&d->pgraph.flush_pending)) {
        qemu_mutex_unlock(&d->pfifo.lock);
        qemu_mutex_lock(&d->pgraph.lock);
        if (qatomic_read(&d->pgraph.sync_pending)) {
            pgraph_android_sync(d);
        }
        if (qatomic_read(&d->pgraph.flush_pending)) {
            pgraph_android_flush(d);
        }
        qemu_mutex_unlock(&d->pgraph.lock);
        qemu_mutex_lock(&d->pfifo.lock);
    }
}

static void pgraph_android_get_report(NV2AState *d, uint32_t parameter)
{
    pgraph_write_zpass_pixel_cnt_report(d, parameter, 0);
}

static void pgraph_android_noop(NV2AState *d)
{
}

static void pgraph_android_noop_u32(NV2AState *d, uint32_t parameter)
{
}

static void pgraph_android_surface_update(NV2AState *d, bool upload,
                                          bool color_write, bool zeta_write)
{
    PGRAPHAndroidState *r = (PGRAPHAndroidState *)d->pgraph.gl_renderer_state;
    if (r && (r->surface_updates++ % 60) == 0) {
        xemu_android_log_info(
            "Android GLES presenter surface update count=%d upload=%d color=%d zeta=%d",
            r->surface_updates, upload, color_write, zeta_write);
    }
}

static PGRAPHRenderer pgraph_android_renderer = {
    .type = CONFIG_DISPLAY_RENDERER_OPENGL,
    .name = "Android GLES",
    .ops = {
        .init = pgraph_android_init,
        .finalize = pgraph_android_finalize,
        .clear_report_value = pgraph_android_noop,
        .clear_surface = pgraph_android_noop_u32,
        .draw_begin = pgraph_android_noop,
        .draw_end = pgraph_android_noop,
        .flip_stall = pgraph_android_noop,
        .flush_draw = pgraph_android_noop,
        .get_report = pgraph_android_get_report,
        .image_blit = pgraph_android_noop,
        .pre_savevm_trigger = pgraph_android_noop,
        .pre_savevm_wait = pgraph_android_noop,
        .pre_shutdown_trigger = pgraph_android_noop,
        .pre_shutdown_wait = pgraph_android_noop,
        .process_pending = pgraph_android_process_pending,
        .process_pending_reports = pgraph_android_noop,
        .surface_update = pgraph_android_surface_update,
    },
};

static void __attribute__((constructor)) register_renderer(void)
{
    pgraph_renderer_register(&pgraph_android_renderer);
}
#endif

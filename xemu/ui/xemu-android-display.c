#include "qemu/osdep.h"

#ifdef __ANDROID__
#include "ui/console.h"
#include "ui/xemu-android-native.h"

static DisplayChangeListener android_dcl;
static bool android_dcl_registered;
static int refresh_count;
static int update_count;

static void android_display_refresh(DisplayChangeListener *dcl)
{
    if (dcl->con) {
        graphic_hw_update(dcl->con);
        refresh_count++;
        if ((refresh_count % 60) == 1) {
            xemu_android_log_info("Android display refresh count=%d", refresh_count);
        }
    }
}

static void android_display_gfx_update(DisplayChangeListener *dcl,
                                       int x, int y, int w, int h)
{
    update_count++;
    if ((update_count % 60) == 1) {
        xemu_android_log_info("Android display gfx update count=%d rect=%d,%d %dx%d",
                              update_count, x, y, w, h);
    }
}

static void android_display_gfx_switch(DisplayChangeListener *dcl,
                                       DisplaySurface *new_surface)
{
    static int switch_count;

    switch_count++;
    if ((switch_count % 120) == 1) {
        xemu_android_log_info("Android display gfx switch count=%d surface=%p",
                              switch_count, new_surface);
    }
}

static const DisplayChangeListenerOps android_display_ops = {
    .dpy_name = "android",
    .dpy_refresh = android_display_refresh,
    .dpy_gfx_update = android_display_gfx_update,
    .dpy_gfx_switch = android_display_gfx_switch,
};

void xemu_android_register_display_listener(void)
{
    if (android_dcl_registered) {
        return;
    }

    memset(&android_dcl, 0, sizeof(android_dcl));
    android_dcl.ops = &android_display_ops;
    android_dcl.con = qemu_console_lookup_default();
    register_displaychangelistener(&android_dcl);
    android_dcl_registered = true;
    xemu_android_log_info("Android display listener registered con=%p",
                          android_dcl.con);
}
#endif

/*
 * Android in-process entrypoint for xemu.
 */

#include "qemu/osdep.h"
#include "qemu-main.h"
#include "qemu/main-loop.h"
#include "system/replay.h"
#include "system/system.h"
#include "ui/xemu-android-native.h"

int xemu_android_main(int argc, char **argv)
{
    int status;

    qemu_init(argc, argv);
    xemu_android_register_display_listener();

    /*
     * qemu_init starts with BQL/replay locks held, matching system/main.c.
     * Release and reacquire them on this native thread before entering the
     * main loop so Android can call us from a detached app-owned thread.
     */
    bql_unlock();
    replay_mutex_unlock();

    replay_mutex_lock();
    bql_lock();
    status = qemu_main_loop();
    qemu_cleanup(status);
    bql_unlock();
    replay_mutex_unlock();

    return status;
}

/*
 * Android core stubs for desktop UI services.
 */

#include "qemu/osdep.h"
#include "ui/xemu-input.h"
#include "ui/xemu-android-native.h"
#include "ui/xemu-net.h"
#include "ui/xemu-notifications.h"
#include "qemu/thread.h"

#include <math.h>

void xemu_queue_notification(const char *msg)
{
    fprintf(stderr, "xemu notification: %s\n", msg ? msg : "");
}

void xemu_queue_error_message(const char *msg)
{
    fprintf(stderr, "xemu error: %s\n", msg ? msg : "");
}

void xemu_net_enable(void)
{
}

void xemu_net_disable(void)
{
}

int xemu_net_is_enabled(void)
{
    return 0;
}

ControllerStateList available_controllers =
    QTAILQ_HEAD_INITIALIZER(available_controllers);
ControllerState *bound_controllers[4];
const char *bound_drivers[4] = {
    DRIVER_S, DRIVER_S, DRIVER_S, DRIVER_S,
};
int *g_keyboard_scancode_map[25];

static QemuMutex android_input_lock;
static bool android_input_lock_initialized;
static bool android_input_initialized;
static ControllerState android_controllers[4];
static uint16_t android_buttons;
static int16_t android_axis[CONTROLLER_AXIS__COUNT];

enum android_input_button {
    ANDROID_BTN_A,
    ANDROID_BTN_B,
    ANDROID_BTN_X,
    ANDROID_BTN_Y,
    ANDROID_BTN_DPAD_LEFT,
    ANDROID_BTN_DPAD_UP,
    ANDROID_BTN_DPAD_RIGHT,
    ANDROID_BTN_DPAD_DOWN,
    ANDROID_BTN_BACK,
    ANDROID_BTN_START,
    ANDROID_BTN_WHITE,
    ANDROID_BTN_BLACK,
    ANDROID_BTN_LSTICK,
    ANDROID_BTN_RSTICK,
};

static const uint16_t android_button_map[] = {
    [ANDROID_BTN_A] = CONTROLLER_BUTTON_A,
    [ANDROID_BTN_B] = CONTROLLER_BUTTON_B,
    [ANDROID_BTN_X] = CONTROLLER_BUTTON_X,
    [ANDROID_BTN_Y] = CONTROLLER_BUTTON_Y,
    [ANDROID_BTN_DPAD_LEFT] = CONTROLLER_BUTTON_DPAD_LEFT,
    [ANDROID_BTN_DPAD_UP] = CONTROLLER_BUTTON_DPAD_UP,
    [ANDROID_BTN_DPAD_RIGHT] = CONTROLLER_BUTTON_DPAD_RIGHT,
    [ANDROID_BTN_DPAD_DOWN] = CONTROLLER_BUTTON_DPAD_DOWN,
    [ANDROID_BTN_BACK] = CONTROLLER_BUTTON_BACK,
    [ANDROID_BTN_START] = CONTROLLER_BUTTON_START,
    [ANDROID_BTN_WHITE] = CONTROLLER_BUTTON_WHITE,
    [ANDROID_BTN_BLACK] = CONTROLLER_BUTTON_BLACK,
    [ANDROID_BTN_LSTICK] = CONTROLLER_BUTTON_LSTICK,
    [ANDROID_BTN_RSTICK] = CONTROLLER_BUTTON_RSTICK,
};

static void android_input_ensure_lock(void)
{
    if (!android_input_lock_initialized) {
        qemu_mutex_init(&android_input_lock);
        android_input_lock_initialized = true;
    }
}

static int16_t android_axis_to_xemu(int axis, float value)
{
    if (axis == CONTROLLER_AXIS_LTRIG || axis == CONTROLLER_AXIS_RTRIG) {
        if (value < 0.0f) {
            value = 0.0f;
        } else if (value > 1.0f) {
            value = 1.0f;
        }
        return (int16_t)(value * 32767.0f);
    }

    if (value < -1.0f) {
        value = -1.0f;
    } else if (value > 1.0f) {
        value = 1.0f;
    }
    if (value <= -1.0f) {
        return -32768;
    }
    return (int16_t)(value * 32767.0f);
}

static void android_input_init_controller(int index)
{
    ControllerState *state = &android_controllers[index];

    memset(state, 0, sizeof(*state));
    state->type = INPUT_DEVICE_SDL_GAMEPAD;
    state->name = index == 0 ? "Android Physical Controller" : "Android Empty Controller";
    state->bound = index;
    state->peripheral_types[0] = PERIPHERAL_NONE;
    state->peripheral_types[1] = PERIPHERAL_NONE;
    bound_controllers[index] = state;
    QTAILQ_INSERT_TAIL(&available_controllers, state, entry);
}

void xemu_set_widescreen(bool widescreen)
{
    (void)widescreen;
}

void xemu_input_init(void)
{
    android_input_ensure_lock();
    qemu_mutex_lock(&android_input_lock);
    if (!android_input_initialized) {
        QTAILQ_INIT(&available_controllers);
        for (int i = 0; i < 4; i++) {
            android_input_init_controller(i);
        }
        android_input_initialized = true;
    }
    qemu_mutex_unlock(&android_input_lock);
    xemu_android_log_info("Android physical controller backend initialized");
}

void xemu_input_update_controllers(void)
{
    for (int i = 0; i < 4; i++) {
        xemu_input_update_controller(&android_controllers[i]);
    }
}

void xemu_input_update_controller(ControllerState *state)
{
    if (!state) {
        return;
    }
    android_input_ensure_lock();
    qemu_mutex_lock(&android_input_lock);
    if (state == &android_controllers[0]) {
        state->buttons = android_buttons;
        memcpy(state->axis, android_axis, sizeof(state->axis));
    } else {
        state->buttons = 0;
        memset(state->axis, 0, sizeof(state->axis));
    }
    qemu_mutex_unlock(&android_input_lock);
}

void xemu_input_update_sdl_kbd_controller_state(ControllerState *state)
{
}

void xemu_input_update_sdl_controller_state(ControllerState *state)
{
}

void xemu_input_update_rumble(ControllerState *state)
{
}

ControllerState *xemu_input_get_bound(int index)
{
    if (index < 0 || index >= 4) {
        return NULL;
    }
    if (!android_input_initialized) {
        xemu_input_init();
    }
    return bound_controllers[index];
}

void xemu_input_bind(int index, ControllerState *state, int save)
{
    if (index >= 0 && index < 4) {
        bound_controllers[index] = state;
    }
}

bool xemu_input_bind_xmu(int player_index, int peripheral_port_index,
                         const char *filename, bool is_rebind)
{
    return false;
}

void xemu_input_rebind_xmu(int port)
{
}

void xemu_input_unbind_xmu(int player_index, int peripheral_port_index)
{
}

int xemu_input_get_controller_default_bind_port(ControllerState *state,
                                                int start)
{
    return start;
}

void xemu_save_peripheral_settings(int player_index, int peripheral_index,
                                   int peripheral_type,
                                   const char *peripheral_parameter)
{
}

void xemu_input_set_test_mode(int enabled)
{
}

int xemu_input_get_test_mode(void)
{
    return 0;
}

void xemu_input_reset_input_mapping(ControllerState *state)
{
}

void xemu_android_input_set_button(int button, bool pressed)
{
    if (button < 0 || button >= (int)G_N_ELEMENTS(android_button_map)) {
        return;
    }
    android_input_ensure_lock();
    qemu_mutex_lock(&android_input_lock);
    if (pressed) {
        android_buttons |= android_button_map[button];
    } else {
        android_buttons &= ~android_button_map[button];
    }
    qemu_mutex_unlock(&android_input_lock);
}

void xemu_android_input_set_axis(int axis, float value)
{
    if (axis < 0 || axis >= CONTROLLER_AXIS__COUNT) {
        return;
    }
    android_input_ensure_lock();
    qemu_mutex_lock(&android_input_lock);
    android_axis[axis] = android_axis_to_xemu(axis, value);
    qemu_mutex_unlock(&android_input_lock);
}

void xemu_android_input_reset(void)
{
    android_input_ensure_lock();
    qemu_mutex_lock(&android_input_lock);
    android_buttons = 0;
    memset(android_axis, 0, sizeof(android_axis));
    qemu_mutex_unlock(&android_input_lock);
}

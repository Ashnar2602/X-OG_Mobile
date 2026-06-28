package emu.xbox.og;

import android.view.KeyEvent;

final class AndroidInputMapper {
    static final int BTN_A = 0;
    static final int BTN_B = 1;
    static final int BTN_X = 2;
    static final int BTN_Y = 3;
    static final int BTN_DPAD_LEFT = 4;
    static final int BTN_DPAD_UP = 5;
    static final int BTN_DPAD_RIGHT = 6;
    static final int BTN_DPAD_DOWN = 7;
    static final int BTN_BACK = 8;
    static final int BTN_START = 9;
    static final int BTN_LB = 10;
    static final int BTN_RB = 11;
    static final int BTN_LS = 12;
    static final int BTN_RS = 13;
    static final int AXIS_LTRIGGER = 0;
    static final int AXIS_RTRIGGER = 1;
    static final int AXIS_LSTICK_X = 2;
    static final int AXIS_LSTICK_Y = 3;
    static final int AXIS_RSTICK_X = 4;
    static final int AXIS_RSTICK_Y = 5;

    private AndroidInputMapper() {
    }

    static int mapKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return BTN_A;
            case KeyEvent.KEYCODE_BUTTON_B: return BTN_B;
            case KeyEvent.KEYCODE_BUTTON_X: return BTN_X;
            case KeyEvent.KEYCODE_BUTTON_Y: return BTN_Y;
            case KeyEvent.KEYCODE_DPAD_LEFT: return BTN_DPAD_LEFT;
            case KeyEvent.KEYCODE_DPAD_UP: return BTN_DPAD_UP;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return BTN_DPAD_RIGHT;
            case KeyEvent.KEYCODE_DPAD_DOWN: return BTN_DPAD_DOWN;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return BTN_BACK;
            case KeyEvent.KEYCODE_BUTTON_START: return BTN_START;
            case KeyEvent.KEYCODE_BUTTON_L1: return BTN_LB;
            case KeyEvent.KEYCODE_BUTTON_R1: return BTN_RB;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return BTN_LS;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return BTN_RS;
            default: return -1;
        }
    }

    static int mapTriggerKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_L2: return AXIS_LTRIGGER;
            case KeyEvent.KEYCODE_BUTTON_R2: return AXIS_RTRIGGER;
            default: return -1;
        }
    }
}

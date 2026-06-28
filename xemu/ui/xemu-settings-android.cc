/*
 * Minimal Android settings backend for the xemu core.
 */

#include "qemu/osdep.h"
#include <glib.h>
#include <string.h>
#include <stdlib.h>
#include <cnode.h>

#include "xemu-settings.h"

#define DEFINE_CONFIG_TREE
#include "xemu-config.h"

struct config g_config;

static const char *settings_path;
static char *base_path;
static char *eeprom_path;

static const char *env_or_empty(const char *name)
{
    const char *value = g_getenv(name);
    return value ? value : "";
}

static bool env_bool(const char *name, bool fallback)
{
    const char *value = g_getenv(name);
    if (!value || !*value) {
        return fallback;
    }
    return g_ascii_strcasecmp(value, "1") == 0 ||
           g_ascii_strcasecmp(value, "true") == 0 ||
           g_ascii_strcasecmp(value, "yes") == 0 ||
           g_ascii_strcasecmp(value, "on") == 0;
}

static double env_double(const char *name, double fallback)
{
    const char *value = g_getenv(name);
    char *end = NULL;
    double parsed;

    if (!value || !*value) {
        return fallback;
    }
    parsed = g_ascii_strtod(value, &end);
    return end && end != value ? parsed : fallback;
}

static CONFIG_DISPLAY_RENDERER env_renderer(void)
{
    const char *value = g_getenv("XEMU_ANDROID_RENDERER");
    if (value && g_ascii_strcasecmp(value, "opengl") == 0) {
        return CONFIG_DISPLAY_RENDERER_OPENGL;
    }
    return CONFIG_DISPLAY_RENDERER_VULKAN;
}

static CONFIG_SYS_AVPACK env_avpack(void)
{
    const char *value = g_getenv("XEMU_ANDROID_AVPACK");
    if (!value) {
        return CONFIG_SYS_AVPACK_HDTV;
    }
    if (g_ascii_strcasecmp(value, "scart") == 0) return CONFIG_SYS_AVPACK_SCART;
    if (g_ascii_strcasecmp(value, "vga") == 0) return CONFIG_SYS_AVPACK_VGA;
    if (g_ascii_strcasecmp(value, "rfu") == 0) return CONFIG_SYS_AVPACK_RFU;
    if (g_ascii_strcasecmp(value, "svideo") == 0) return CONFIG_SYS_AVPACK_SVIDEO;
    if (g_ascii_strcasecmp(value, "composite") == 0) return CONFIG_SYS_AVPACK_COMPOSITE;
    if (g_ascii_strcasecmp(value, "none") == 0) return CONFIG_SYS_AVPACK_NONE;
    return CONFIG_SYS_AVPACK_HDTV;
}

void xemu_settings_set_path(const char *path)
{
    g_free((char *)settings_path);
    settings_path = g_strdup(path);
}

const char *xemu_settings_get_base_path(void)
{
    if (!base_path) {
        base_path = g_strdup(g_get_user_config_dir());
    }
    return base_path;
}

const char *xemu_settings_get_path(void)
{
    if (!settings_path) {
        settings_path = g_build_filename(xemu_settings_get_base_path(),
                                         "xemu.toml", NULL);
    }
    return settings_path;
}

const char *xemu_settings_get_default_eeprom_path(void)
{
    if (!eeprom_path) {
        eeprom_path = g_build_filename(xemu_settings_get_base_path(),
                                       "eeprom.bin", NULL);
    }
    return eeprom_path;
}

const char *xemu_settings_get_error_message(void)
{
    return NULL;
}

bool xemu_settings_load(void)
{
    config_tree.store_to_struct(&g_config);
    g_config.general.show_welcome = false;
    g_config.general.skip_boot_anim = env_bool("XEMU_ANDROID_SKIP_BOOT_ANIM", false);
    g_config.display.renderer = env_renderer();
    g_config.audio.use_dsp_jit = false;
    g_config.audio.volume_limit = env_bool("XEMU_ANDROID_AUDIO_MUTED", false) ?
        0.0f : CLAMP(env_double("XEMU_ANDROID_AUDIO_VOLUME", 1.0), 0.0, 1.0);
    g_config.sys.avpack = env_avpack();
    g_config.sys.files.bootrom_path = g_strdup(env_or_empty("XEMU_ANDROID_MCPX"));
    g_config.sys.files.flashrom_path = g_strdup(env_or_empty("XEMU_ANDROID_BIOS"));
    g_config.sys.files.hdd_path = g_strdup(env_or_empty("XEMU_ANDROID_HDD"));
    g_config.sys.files.dvd_path = g_strdup(env_or_empty("XEMU_ANDROID_DISC"));
    return true;
}

void xemu_settings_save(void)
{
}

void add_net_nat_forward_ports(int host, int guest,
                               CONFIG_NET_NAT_FORWARD_PORTS_PROTOCOL protocol)
{
}

void remove_net_nat_forward_ports(unsigned int index)
{
}

bool xemu_settings_load_gamepad_mapping(const char *guid,
                                        GamepadMappings **mapping)
{
    if (mapping) {
        *mapping = NULL;
    }
    return false;
}

void xemu_settings_reset_controller_mapping(const char *guid)
{
}

void xemu_settings_reset_keyboard_mapping(void)
{
}

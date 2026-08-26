/*
 * QEMU MCPX Audio Processing Unit implementation
 *
 * Copyright (c) 2019-2025 Matt Borgerson
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, see <http://www.gnu.org/licenses/>.
 */

#include "apu_int.h"

#ifdef __ANDROID__
#include "ui/xemu-android-native.h"
#endif

void mcpx_apu_monitor_init(MCPXAPUState *d, Error **errp)
{
#ifdef __ANDROID__
    d->monitor.stream = xemu_android_audio_init() ? (void *)1 : NULL;
    /*
     * Keep a healthy cushion inside the audio ring. The AAudio callback
     * drains 256 frames every ~5.3ms; under TCG load the APU producer
     * thread can be delayed by tens of milliseconds, so tiny watermarks
     * (the previous 1KB/3KB = ~5/16ms) let the ring run dry, producing
     * intermittent silence. 30ms low / 90ms high trades a little extra
     * latency for dropout resistance. The ring itself holds 8192 frames.
     */
    int android_frame_bytes = 2 * sizeof(int16_t);
    d->monitor.queued_bytes_low = (48000 / 1000) * 30 * android_frame_bytes;
    d->monitor.queued_bytes_high = (48000 / 1000) * 90 * android_frame_bytes;
#else
    SDL_AudioSpec spec = {
        .freq = 48000,
        .format = SDL_AUDIO_S16LE,
        .channels = 2,
    };

    d->monitor.stream = NULL;

    if (!SDL_Init(SDL_INIT_AUDIO)) {
        error_setg(errp, "SDL_Init failed: %s", SDL_GetError());
        return;
    }

    d->monitor.stream = SDL_OpenAudioDeviceStream(
        SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK, &spec, NULL, NULL);
    if (d->monitor.stream == NULL) {
        error_setg(errp, "SDL_OpenAudioDeviceStream failed: %s",
                   SDL_GetError());
        return;
    }

    SDL_AudioDeviceID dev = SDL_GetAudioStreamDevice(d->monitor.stream);

    SDL_AudioSpec dev_spec;
    int dev_buf_frames = 0;
    int dev_drain_bytes = 0;
    if (SDL_GetAudioDeviceFormat(dev, &dev_spec, &dev_buf_frames)) {
        dev_drain_bytes = dev_buf_frames * spec.channels *
                          SDL_AUDIO_BYTESIZE(spec.format) *
                          spec.freq / dev_spec.freq;
    }
    int frame_bytes = sizeof(d->monitor.frame_buf);
    int drain = MAX(dev_drain_bytes, frame_bytes);
    d->monitor.queued_bytes_low = drain;
    d->monitor.queued_bytes_high = 3 * drain;

    SDL_ResumeAudioDevice(dev);
#endif
}

void mcpx_apu_monitor_finalize(MCPXAPUState *d)
{
#ifdef __ANDROID__
    if (d->monitor.stream) {
        xemu_android_audio_shutdown();
        d->monitor.stream = NULL;
    }
#else
    if (d->monitor.stream) {
        SDL_DestroyAudioStream(d->monitor.stream);
    }
#endif
}

void mcpx_apu_monitor_frame(MCPXAPUState *d)
{
    if ((d->ep_frame_div + 1) % 8) {
        return;
    }

    if (d->monitor.stream) {
#ifdef __ANDROID__
        float vu = pow(fmax(0.0, fmin(g_config.audio.volume_limit, 1.0)), M_E);
        int16_t out[256][2];

        for (size_t i = 0; i < ARRAY_SIZE(out); i++) {
            for (size_t ch = 0; ch < ARRAY_SIZE(out[i]); ch++) {
                float sample = d->monitor.frame_buf[i][ch] * vu;
                sample = fmaxf(INT16_MIN, fminf(INT16_MAX, sample));
                out[i][ch] = sample;
            }
        }
        xemu_android_audio_queue((const int16_t *)out, ARRAY_SIZE(out));
#else
        float vu = pow(fmax(0.0, fmin(g_config.audio.volume_limit, 1.0)), M_E);
        SDL_SetAudioStreamGain(d->monitor.stream, vu);
        SDL_PutAudioStreamData(d->monitor.stream, d->monitor.frame_buf,
                            sizeof(d->monitor.frame_buf));
#endif
    }

    memset(d->monitor.frame_buf, 0, sizeof(d->monitor.frame_buf));
}

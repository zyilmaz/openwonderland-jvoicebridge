/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License version 2 as 
 * published by the Free Software Foundation and distributed hereunder 
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this 
 * code. 
 */

#include <stdio.h>
#include <errno.h>
#include <netinet/in.h>

#include <alsa/asoundlib.h>

snd_pcm_format_t format = SND_PCM_FORMAT_S16_BE; /* signed 16-bit big endian */
unsigned int period_time = 20000;   /* period time in microseconds */
int resample = 1;                   /* enable alsa-lib resampling */

static snd_output_t *output = NULL;

snd_pcm_uframes_t get_period_frames(snd_pcm_t *handle);
snd_pcm_uframes_t get_buffer_frames(snd_pcm_t *handle);

void dump_hwparams(snd_pcm_t * handle);
void dump_swparams(snd_pcm_t * handle);

int 
set_hwparams(snd_pcm_t *handle, int sampleRate, int channels, int bufferSize)
{
    snd_pcm_access_t access = SND_PCM_ACCESS_RW_INTERLEAVED;

    int ret, dir;

    snd_pcm_hw_params_t *hwparams;

    snd_pcm_hw_params_alloca(&hwparams);

    /* choose all parameters */
    ret = snd_pcm_hw_params_any(handle, hwparams);
    if (ret < 0) {
        fprintf(stderr, "Broken configuration for playback: "
	    "no configurations available: %s\n", snd_strerror(ret));
        return ret;
    }
    /* set hardware resampling */
    ret = snd_pcm_hw_params_set_rate_resample(handle, hwparams, resample);
    if (ret < 0) {
	fprintf(stderr, "Resampling setup failed for playback: %s\n", 
	    snd_strerror(ret));
        return ret;
    }

    /* set the interleaved read/write format */
    ret = snd_pcm_hw_params_set_access(handle, hwparams, access);
    if (ret < 0) {
        fprintf(stderr, "Access type not available for playback: %s\n", 
	    snd_strerror(ret));
        return ret;
    }

    /* set the sample format */
    ret = snd_pcm_hw_params_set_format(handle, hwparams, format);
    if (ret < 0) {
        fprintf(stderr, "Sample format not available for playback: %s\n", 
	    snd_strerror(ret));
        return ret;
    }

    /* set the count of channels */
    ret = snd_pcm_hw_params_set_channels(handle, hwparams, channels);
    if (ret < 0) {
        fprintf(stderr, 
	    "Channels count (%d) not available for playbacks: %s\n", 
	    channels, snd_strerror(ret));
        return ret;
    } 

    /* set the stream rate */
    int rate = sampleRate;

    ret = snd_pcm_hw_params_set_rate(handle, hwparams, rate, 0);
    if (ret < 0) {
        fprintf(stderr, "Rate %dHz not available for playback: %s\n", 
	    sampleRate, snd_strerror(ret));
        return ret;
    }

    if (rate != sampleRate) {
        fprintf(stderr, "Rate doesn't match (requested %dHz, get %dHz)\n", 
	    rate, ret);
        return -EINVAL;
    } 

    /* set the buffer frames */
    unsigned int buffer_frames = bufferSize / 2 / channels;  // bytes to frames

    ret = snd_pcm_hw_params_set_buffer_size(handle, hwparams, buffer_frames);
    if (ret < 0) {
        fprintf(stderr, "Unable to set buffer size %d for playback: %s\n", 
	    buffer_frames, snd_strerror(ret));
        return ret;
    }

    /* set the period time */
    dir = 0;
    ret = snd_pcm_hw_params_set_period_time_near(handle, hwparams, 
	&period_time, &dir);
    if (ret < 0) {
        fprintf(stderr, "Unable to set period time %d for playback: %s\n", 
	    period_time, snd_strerror(ret));
        return ret;
    }

    /* write the parameters to device */
    ret = snd_pcm_hw_params(handle, hwparams);
    if (ret < 0) {
        fprintf(stderr, "Unable to set hw params for playback: %s\n", 
	    snd_strerror(ret));
        return ret;
    }

    dump_hwparams(handle);
}

int
set_mic_swparams(snd_pcm_t *handle) 
{
    // set software params

    snd_pcm_sw_params_t *swparams;
    snd_pcm_sw_params_alloca(&swparams);

    /* get the current swparams */
    int ret = snd_pcm_sw_params_current(handle, swparams);
    if (ret < 0) {
        fprintf(stderr, 
	    "Unable to determine current swparams for mic: %s\n", 
	    snd_strerror(ret));
        return ret;
    }

    /* allow transfer when at least period_frames can be processed */

    ret = snd_pcm_sw_params_set_avail_min(handle, swparams, 
	get_period_frames(handle));

    if (ret < 0) {
        fprintf(stderr, "Unable to set avail min for mic: %s\n", 
	    snd_strerror(ret));
        return ret;
    }

    /* align all transfers to 1 sample */
    ret = snd_pcm_sw_params_set_xfer_align(handle, swparams, 1);
    if (ret < 0) {
        fprintf(stderr, "Unable to set transfer align for mic: %s\n", 
	    snd_strerror(ret));
        return ret;
    }

    /* write the parameters to the microphone device */
    ret = snd_pcm_sw_params(handle, swparams);
    if (ret < 0) {
        fprintf(stderr, "Unable to set sw params for mic: %s\n", 
	    snd_strerror(ret));
        return ret;
    }
	
    dump_swparams(handle);
    return 0;
}

int
set_speaker_swparams(snd_pcm_t *handle) 
{
    // set software params

    snd_pcm_sw_params_t *swparams;
    snd_pcm_sw_params_alloca(&swparams);

    /* get the current swparams */
    int ret = snd_pcm_sw_params_current(handle, swparams);
    if (ret < 0) {
        fprintf(stderr, 
	    "Unable to determine current swparams for playback: %s\n", 
	    snd_strerror(ret));
        return ret;
    }

    /* start the transfer when the second buffer is written
    ret = snd_pcm_sw_params_set_start_threshold(handle, swparams, 
	2 * period_frames);
    if (ret < 0) {
        fprintf(stderr, 
	    "Unable to set start threshold mode for playback: %s\n", 
	    snd_strerror(ret));
        return ret;
    }

    /* allow transfer when at least period_frames can be processed */

    ret = snd_pcm_sw_params_set_avail_min(handle, swparams, 
	get_period_frames(handle));

    if (ret < 0) {
        fprintf(stderr, "Unable to set avail min for playback: %s\n", 
	    snd_strerror(ret));
        return ret;
    }

    /* align all transfers to 1 sample */
    ret = snd_pcm_sw_params_set_xfer_align(handle, swparams, 1);
    if (ret < 0) {
        fprintf(stderr, "Unable to set transfer align for playback: %s\n", 
	    snd_strerror(ret));
        return ret;
    }

    /* set silence threshold */
    ret = snd_pcm_sw_params_set_silence_threshold(handle, swparams, 160);
    if (ret < 0) {
        fprintf(stderr, "Unable to set sw params for silence_threshold: %s\n", 
	    snd_strerror(ret));
        return ret;
    }

    /* set silence size */
    ret = snd_pcm_sw_params_set_silence_size(handle, swparams, 160);
    if (ret < 0) {
        fprintf(stderr, "Unable to set sw params for silence_size: %s\n", 
	    snd_strerror(ret));
        return ret;
    }

    /* write the parameters to the playback device */
    ret = snd_pcm_sw_params(handle, swparams);
    if (ret < 0) {
        fprintf(stderr, "Unable to set sw params for playback: %s\n", 
	    snd_strerror(ret));
        return ret;
    }
	
    dump_swparams(handle);
    return 0;
}

snd_pcm_uframes_t
get_period_frames(snd_pcm_t *handle)
{
    snd_pcm_uframes_t buffer_frames;
    snd_pcm_uframes_t period_frames;

    int ret = snd_pcm_get_params(handle, &buffer_frames, &period_frames);
    if (ret < 0) {
        fprintf(stderr, "Unable to set buffer frames %d for playback: %s\n",
            buffer_frames, snd_strerror(ret));
        return ret;
    }

    return period_frames;
}

snd_pcm_uframes_t
get_buffer_frames(snd_pcm_t *handle)
{
    snd_pcm_uframes_t buffer_frames;
    snd_pcm_uframes_t period_frames;

    int ret = snd_pcm_get_params(handle, &buffer_frames, &period_frames);
    if (ret < 0) {
        fprintf(stderr, "Unable to set buffer frames %d for playback: %s\n",
            buffer_frames, snd_strerror(ret));
        return ret;
    }

    return buffer_frames;
}

void 
dump_hwparams(snd_pcm_t *handle)
{
    //if (output == NULL) {
    //    ret = snd_output_stdio_attach(&output, stderr, 0);
    //    if (ret < 0) {
    //        fprintf(stderr, "Output failed: %s\n", snd_strerror(ret));
    //        return ret;
    //    }
    //}
    //
    //fprintf(stderr, "hw params\n");
    //snd_pcm_dump_hw_setup(handle, output);
}

void
dump_swparams(snd_pcm_t *handle)
{
    //if (output == NULL) {
    //    ret = snd_output_stdio_attach(&output, stderr, 0);
    //    if (ret < 0) {
    //        fprintf(stderr, "Output failed: %s\n", snd_strerror(ret));
    //        return ret;
    //    }
    //}
    //fprintf(stderr, "sw params\n");
    //snd_pcm_dump_sw_setup(speaker_handle, output);
}

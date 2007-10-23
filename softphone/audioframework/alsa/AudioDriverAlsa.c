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

#include <jni.h>

#include "com_sun_mc_softphone_media_alsa_AudioDriverAlsa.h"

snd_pcm_t *microphone_handle;
int microphone_channels;

snd_pcm_t *speaker_handle;
int speaker_channels;
snd_pcm_uframes_t speaker_buffer_frames;

int set_hwparams(snd_pcm_t *handle, int sampleRate, int channels, int buffer_time);
int set_swparams(snd_pcm_t *handle);

void closeMic();
void closeSpeaker();

snd_pcm_uframes_t get_period_frames(snd_pcm_t *);
int set_callback(snd_pcm_t *handle);
void async_callback(snd_async_handler_t *ahandler);

/******************************************************************************/
JNIEXPORT void JNICALL 
Java_com_sun_mc_softphone_media_alsa_AudioDriverAlsa_nStop(
	JNIEnv * env, jobject jobj) 
{
    closeMic();
    closeSpeaker();
}

/******************************************************************************/
JNIEXPORT int JNICALL 
Java_com_sun_mc_softphone_media_alsa_AudioDriverAlsa_nInitializeMicrophone(
	JNIEnv * env, jobject jobj, jstring device, jint sampleRate, 
	jint channels, jint bufferSize) 
{
    closeMic();

    int ret;

    const char *str = (*env)->GetStringUTFChars(env, device, NULL);

    str = strdup(str);

    //fprintf(stderr, "init mic %s %d\n", str, strlen(str));

    ret = snd_pcm_open(&microphone_handle, str, SND_PCM_STREAM_CAPTURE, 0);

    if (ret < 0) {
        fprintf(stderr, "ALSA initialize microphone error: %s\n", 
	    snd_strerror(ret));
	return ret;
    }

    ret = set_hwparams(microphone_handle, sampleRate, channels, bufferSize);

    if (ret < 0) {
	return ret;
    }

    ret = set_mic_swparams(microphone_handle);

    if (ret < 0) {
	return ret;
    }

    microphone_channels = channels;
    return 0;
}

/******************************************************************************/
JNIEXPORT jint JNICALL 
Java_com_sun_mc_softphone_media_alsa_AudioDriverAlsa_nMicrophoneAvailable(
	JNIEnv * env, jobject jobj) {

    if (microphone_handle == NULL) {
        //fprintf(stderr, "Available:  microphone_handle is NULL\n");
        return -1;
    }

    int avail_frames = snd_pcm_avail_update(microphone_handle);

    if (avail_frames < 0) {
	fprintf(stderr, "microphoneAvailable returned %d\n", avail_frames);
        return avail_frames;
    }

    return avail_frames * 2 * microphone_channels;  // convert to bytes
}

/******************************************************************************/
JNIEXPORT jint JNICALL 
Java_com_sun_mc_softphone_media_alsa_AudioDriverAlsa_nReadMic(
	JNIEnv * env, jobject jobj, jshortArray micBuffer, jint shortLen)
{    
    if (microphone_handle == NULL) {
	return -1;
    }

    int frames = shortLen / microphone_channels;

    jshortArray shortArray = (*env)->NewShortArray(env, shortLen * 2);
    jshort *shortBuffer = (*env)->GetShortArrayElements(env, shortArray, NULL);

    frames = snd_pcm_readi(microphone_handle, shortBuffer, frames);

    if (frames < 0) {
	frames = snd_pcm_recover(microphone_handle, frames, 1);

	if (frames < 0) {
	    fprintf(stderr, "nReadMic:  Unable to read mic, error %s.\n", 
	        snd_strerror(frames));
	}
	return frames;
    }
    
    int i;

    /*
     * byte swap
     */
    for (i = 0; i < shortLen; i++) {
        shortBuffer[i] = htons(shortBuffer[i]);
    }

    /*
     * Copy data to user buffer
     */
    (*env)->SetShortArrayRegion(env, micBuffer, 0, shortLen, shortBuffer);
    (*env)->ReleaseShortArrayElements(env, shortArray, shortBuffer, 0);

    return frames * 2 * microphone_channels;	// convert frames to bytes
}

/******************************************************************************/
JNIEXPORT int JNICALL 
Java_com_sun_mc_softphone_media_alsa_AudioDriverAlsa_nFlushMicrophone(
	JNIEnv * env, jobject jobj) {

    if (microphone_handle == NULL) {
	return 0;
    }

    int ret = snd_pcm_drop(microphone_handle);

    ret = snd_pcm_prepare(microphone_handle);

    if (ret < 0) {
	fprintf(stderr, "Unable to prepare mic after flush %s\n", 
	    snd_strerror(ret));
	return ret;
    }

    return 0;
}

void 
closeMic() 
{
    if (microphone_handle != NULL) {
        snd_pcm_close(microphone_handle);
	microphone_handle = NULL;
    }
}

/******************************************************************************/
JNIEXPORT int JNICALL 
Java_com_sun_mc_softphone_media_alsa_AudioDriverAlsa_nInitializeSpeaker(
	JNIEnv * env, jobject jobj, jstring device, jint sampleRate, 
	jint channels, jint bufferSize) 
{
    closeSpeaker();

    int ret;

    const char *str = (*env)->GetStringUTFChars(env, device, NULL);

    str = strdup(str);

    //fprintf(stderr, "init speaker %s %d, rate %d, ch %d, size %d\n", str,
    //	strlen(str), sampleRate, channels, bufferSize);

    ret = snd_pcm_open(&speaker_handle, str, SND_PCM_STREAM_PLAYBACK, 0);
	//SND_PCM_NONBLOCK);

    if (ret < 0) {
        fprintf(stderr, "ALSA initialize speaker error: %s\n", 
	    snd_strerror(ret));
	return -1;
    }

    ret = set_hwparams(speaker_handle, sampleRate, channels, bufferSize);

    if (ret < 0) {
	return ret;
    }

    ret = set_speaker_swparams(speaker_handle, channels, bufferSize);

    if (ret < 0) {
	return ret;
    }

    speaker_channels = channels;
    speaker_buffer_frames = get_buffer_frames(speaker_handle);

    set_callback(speaker_handle);
    return 0;
}

JNIEXPORT jint JNICALL 
Java_com_sun_mc_softphone_media_alsa_AudioDriverAlsa_nSpeakerAvailable(
	JNIEnv * env, jobject jobj) 
{
    if (speaker_handle == 0) {
	fprintf(stderr, "Available:  speaker_handle is NULL\n");
	return -1;
    }

    snd_pcm_uframes_t used_frames;
    int ret = snd_pcm_delay(speaker_handle, &used_frames);

    if (ret < 0) {
	//fprintf(stderr, "Underrun...\n");
    	return speaker_buffer_frames;
    }

    int avail_frames = speaker_buffer_frames - used_frames;

#if 0
    //snd_pcm_hwsync(speaker_handle);

    avail_frames = snd_pcm_avail_update(speaker_handle);

    if (avail_frames < 0) {
	int ret = snd_pcm_recover(speaker_handle, avail_frames, 1);

	if (ret < 0) {
	    fprintf(stderr, "speakerAvailable returned %s\n", 
	        snd_strerror(avail_frames));
	    return frames;
	}

	fprintf(stderr, "speakerAvailable recovered\n");

	avail_frames = snd_pcm_avail_update(speaker_handle);

	if (avail_frames < 0) {
	    fprintf(stderr, 
		"failed to recover from snd_pcm_avail_update: %s\n",
		snd_strerror(avail_frames));
	    return;
	}
    }
#endif

    return avail_frames * 2 * speaker_channels;	// convert frames to bytes
}

JNIEXPORT jint JNICALL 
Java_com_sun_mc_softphone_media_alsa_AudioDriverAlsa_nWriteSpeaker(
	JNIEnv * env, jobject jobj, jobjectArray speakerBuffer, jint shortLen)
{
    if (speaker_handle == NULL) {
	return -1;
    }

    int logLevel = shortLen >> 16;
    shortLen &= 0xffff;

    jshort *buffer = (*env)->GetShortArrayElements(env, speakerBuffer, NULL);

    int frames = shortLen / speaker_channels;

    int ret = snd_pcm_writei(speaker_handle, buffer, frames);

    //fprintf(stderr, "write speaker %d, %d\n", frames, ret);

    if (ret < 0) {
	fprintf(stderr, "write speaker error:  %s\n", snd_strerror(ret));

	if (snd_pcm_recover(speaker_handle, ret, 1) < 0) {
	    fprintf(stderr, "nWriteSpeaker failed to recover from writei:  %s\n", 
		snd_strerror(ret));
	    return ret;
	}

	/*
	 * Retry
	 */
    	ret = snd_pcm_writei(speaker_handle, buffer, frames);

	if (ret < 0) {
	    fprintf(stderr, "writei retry failed:  %s\n", snd_strerror(ret));
	    return ret;
	}
    }

    if (logLevel > 3) {
        fprintf(stderr, "write len %d, %x %x %x %x\n",
	    ret * 2 * speaker_channels,
	    buffer[0], buffer[1], buffer[2], buffer[3], buffer[4]);
    }
	
    (*env)->ReleaseShortArrayElements(env, speakerBuffer, buffer, 0);

    return ret * 2 * speaker_channels;
}

JNIEXPORT int JNICALL 
Java_com_sun_mc_softphone_media_alsa_AudioDriverAlsa_nFlushSpeaker(
	JNIEnv * env, jobject jobj) {

    if (speaker_handle == NULL) {
	return 0;
    }

    int ret = snd_pcm_drop(speaker_handle);

    ret = snd_pcm_prepare(speaker_handle);

    if (ret < 0) {
	fprintf(stderr, "Unable to prepare speaker after flush %s\n", 
	    snd_strerror(ret));
	return ret;
    }

    return 0;
}

void 
closeSpeaker() 
{
    if (speaker_handle != NULL) {
        snd_pcm_close(speaker_handle);
	speaker_handle = NULL;
    }
}

short *silence;
snd_pcm_uframes_t silence_frames;
snd_pcm_uframes_t period_frames;

int 
set_callback(snd_pcm_t *handle)
{
    int data;
    snd_async_handler_t *ahandler;
    int ret;

    ret = snd_async_add_pcm_handler(&ahandler, handle, async_callback, &data);

    if (ret == 0) {
	period_frames = get_period_frames(handle);

	if (ret < 0) {
	    fprintf(stderr, "Unable to get speaker period frames: %s\n",
		snd_strerror(ret));
	    return;
	}

	//fprintf(stderr, "speaker period size %d\n", period_frames);

	silence_frames = period_frames;
	silence = (short *) malloc(silence_frames * 2 * speaker_channels);
	bzero(silence, silence_frames * 2 * speaker_channels);
	return;
    }

    fprintf(stderr, "Unable to register async handler\n");
}

void 
async_callback(snd_async_handler_t *ahandler)
{
    snd_pcm_t *handle = snd_async_handler_get_pcm(ahandler);
    struct async_private_data *data = snd_async_handler_get_callback_private(ahandler);

    snd_pcm_uframes_t avail_frames = snd_pcm_avail_update(handle);

    int used_frames = speaker_buffer_frames - avail_frames;

    if (used_frames > period_frames) {
        return;
    }

    //fprintf(stderr, "callback:  speaker buffer frames %d, used frames %d\n",
    //	speaker_buffer_frames, used_frames); 

    int ret = snd_pcm_writei(handle, silence, silence_frames);
    if (ret < 0) {
        fprintf(stderr, "write speaker silence error:  %s\n", 
	    snd_strerror(ret));

        if (snd_pcm_recover(handle, ret, 0) < 0) {
            fprintf(stderr, "async_callback failed to recover from writei:  %s\n",
                snd_strerror(ret));
            return;
        }

        /*
         * Retry
         */
        ret = snd_pcm_writei(handle, silence, silence_frames);

        if (ret < 0) {
            fprintf(stderr, "writei retry failed:  %s\n", snd_strerror(ret));
            return;
        }
    }

    //fprintf(stderr, "callback:  wrote %d bytes of silence\n", 
    //    silence_frames * 2 * speaker_channels);
}

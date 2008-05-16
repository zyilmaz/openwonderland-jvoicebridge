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

#include <pthread.h>

#include "com_sun_mc_softphone_media_alsa_AudioDriverAlsa.h"

snd_pcm_t *microphone_handle;
char *microphone_device;
int microphone_buffer_size;
int microphone_sample_rate;
int microphone_channels;

snd_pcm_t *speaker_handle;
char *speaker_device;
int speaker_buffer_size;
int speaker_sample_rate;
int speaker_channels;
snd_pcm_uframes_t speaker_buffer_frames;

int set_hwparams(snd_pcm_t *handle, int sampleRate, int channels, int buffer_time);
int set_swparams(snd_pcm_t *handle);

void closeMic();
void closeSpeaker();

snd_pcm_uframes_t get_period_frames(snd_pcm_t *);
int set_callback(snd_pcm_t *handle);
void async_callback(snd_async_handler_t *ahandler);

typedef struct speaker_data {
    struct speaker_data *next;
    int frames;
    jshort *data;
} speaker_data_t;

speaker_data_t *speaker_data;

speaker_data_t *last_speaker_data;

short *silence;
snd_pcm_uframes_t silence_frames;
snd_pcm_uframes_t period_frames;

pthread_mutex_t *speaker_mutex;

FILE *fp;

void p(char *msg) 
{
#if 0
    //if (fp == NULL) {
    //    fp = fopen("/tmp/alsa.out", "w");
    //}

    //fprintf(fp, "%X: %s\n", pthread_self(), msg);
    //fflush(fp);

    fprintf(stderr, "%X: %s\n", pthread_self(), msg);
    fflush(stderr);
#endif
}

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
    const char *str = (*env)->GetStringUTFChars(env, device, NULL);

    microphone_device = strdup(str);

    microphone_buffer_size = bufferSize;
    
    microphone_sample_rate = sampleRate;

    microphone_channels = channels;

    p("nInitialiMicrophone");

    return initialize_microphone();
}

int initialize_microphone() {
    p("initialize_microphone");

    closeMic();

    int ret = snd_pcm_open(&microphone_handle, microphone_device, SND_PCM_STREAM_CAPTURE, 0);

    if (ret < 0) {
        fprintf(stderr, "ALSA initialize microphone error: %s\n", 
	    snd_strerror(ret));
	return ret;
    }

    ret = set_hwparams(microphone_handle, microphone_sample_rate, 
	microphone_channels, microphone_buffer_size);

    if (ret < 0) {
	return ret;
    }

    ret = set_mic_swparams(microphone_handle);

    if (ret < 0) {
	return ret;
    }

    flushMicrophone();
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

    return flushMicrophone();
}

int flushMicrophone() {
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
	p("closeMic");
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
    char *str = (*env)->GetStringUTFChars(env, device, NULL);

    str = strdup(str);

    speaker_device = str;
    speaker_buffer_size = bufferSize;
    speaker_sample_rate = sampleRate;
    speaker_channels = channels;

    p("nInitializeSpeaker");
    return initialize_speaker();
}

int initialize_speaker() {
    if (speaker_mutex == NULL) {
	p("initialize speaker mutex");
	speaker_mutex = malloc(sizeof (pthread_mutex_t));

	pthread_mutex_init(speaker_mutex, NULL);
    }

    p("initialize_speaker");

    closeSpeaker();

    int ret;

    ret = snd_pcm_open(&speaker_handle, speaker_device, 
	SND_PCM_STREAM_PLAYBACK, 0); //SND_PCM_NONBLOCK);

    if (ret < 0) {
        fprintf(stderr, "ALSA initialize speaker error: %s\n", 
	    snd_strerror(ret));
	return -1;
    }

    ret = set_hwparams(speaker_handle, speaker_sample_rate,
	speaker_channels, speaker_buffer_size);

    if (ret < 0) {
	return ret;
    }

    ret = set_speaker_swparams(speaker_handle, speaker_channels, 
	speaker_buffer_size);

    if (ret < 0) {
	return ret;
    }

    speaker_buffer_frames = get_buffer_frames(speaker_handle);

    set_callback(speaker_handle);

    snd_pcm_prepare(speaker_handle);

    write_speaker(speaker_handle, silence, silence_frames);
    write_speaker(speaker_handle, silence, silence_frames);
    return 0;
}

JNIEXPORT jint JNICALL 
Java_com_sun_mc_softphone_media_alsa_AudioDriverAlsa_nSpeakerAvailable(
	JNIEnv * env, jobject jobj) 
{
    if (speaker_handle == 0) {
	//fprintf(stderr, "Available:  speaker_handle is NULL\n");
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
	    return avail_frames;
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

    shortLen &= 0xffff;

    jshort *buffer = (*env)->GetShortArrayElements(env, speakerBuffer, NULL);

    int frames = shortLen / speaker_channels;

    int ret = write_speaker(speaker_handle, buffer, frames);

    (*env)->ReleaseShortArrayElements(env, speakerBuffer, buffer, 0);

    if (ret <= 0) {
	return 0;
    }

    return shortLen * 2;
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
    p("closeSpeaker");
    p("back from close speaker");

    if (speaker_handle == NULL) {
	return;
    }

    snd_pcm_close(speaker_handle);
    speaker_handle = NULL;
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
    //struct async_private_data *private_data = snd_async_handler_get_callback_private(ahandler);

    int frames;
    jshort *data = NULL;

    speaker_data_t *s;

    snd_pcm_uframes_t avail_frames = snd_pcm_avail_update(handle);

    int used_frames = speaker_buffer_frames - avail_frames;

    if (used_frames >= period_frames) {
        return;
    }

    fprintf(stderr, "Used frames %d, buf frames %d, writing silence frames %d!\n",
    	used_frames, speaker_buffer_frames, silence_frames);

    //if (used_frames == 0) {
    //	fprintf(stderr, "Used frames is zero!\n");
    //}

    write_speaker(handle, silence, silence_frames);
}

int recovering;

int write_speaker(snd_pcm_t *handle, jshort *data, int frames) {
    if (recovering) {
	fprintf(stderr, "dropping data during recovery!\n");
	return 0;
    }

    pthread_mutex_lock(speaker_mutex);

    int ret = snd_pcm_writei(handle, data, frames);

    if (ret < 0) {
        //fprintf(stderr, "write speaker error:  %s\n", 
	//    snd_strerror(ret));

        if (snd_pcm_recover(handle, ret, 0) < 0) {
	    recovering++;
            fprintf(stderr, 
		"write speaker failed to recover from writei:  "
		"%s reinitializing the speaker\n", snd_strerror(ret));

	    //initialize_microphone();
	    initialize_speaker();
	    recovering--;
	}

	ret = 0;
    }

    pthread_mutex_unlock(speaker_mutex);

    return ret;
}

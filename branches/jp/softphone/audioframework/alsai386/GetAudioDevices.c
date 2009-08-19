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

jobjectArray getAudioDevices(JNIEnv * env, snd_pcm_stream_t type);

/******************************************************************************/
/* native methods */
/******************************************************************************/
JNIEXPORT jobjectArray JNICALL 
Java_com_sun_mc_softphone_media_alsa_AudioDriverAlsa_nGetInputDevices(
        JNIEnv * env, jobject jobj) 
{  
    return getAudioDevices(env, SND_PCM_STREAM_CAPTURE);
}

/******************************************************************************/
JNIEXPORT jobjectArray JNICALL 
Java_com_sun_mc_softphone_media_alsa_AudioDriverAlsa_nGetOutputDevices(
	JNIEnv * env, jobject jobj) 
{
    return getAudioDevices(env, SND_PCM_STREAM_PLAYBACK);
}

/******************************************************************************/
jobjectArray getAudioDevices(JNIEnv * env, snd_pcm_stream_t type) 
{
    snd_ctl_t *handle;
    int card, ret, dev, idx;
    snd_ctl_card_info_t *info;
    snd_pcm_info_t *pcminfo;
    snd_ctl_card_info_alloca(&info);
    snd_pcm_info_alloca(&pcminfo);

    card = -1;

    //fprintf(stderr, "**** List of %s Hardware Devices ****\n",
    //    snd_pcm_stream_name(type));

    char *deviceInfo[32]; 

    int i;

    int deviceIndex = 0;

    while (snd_card_next(&card) >= 0 && card >= 0) {
	char name[32];

	sprintf(name, "hw:%d", card);

	if ((ret = snd_ctl_open(&handle, name, 0)) < 0) {
	    fprintf(stderr, "control open (%i): %s\n", card, snd_strerror(ret));
	    continue;
	}

	if ((ret = snd_ctl_card_info(handle, info)) < 0) {
	    fprintf(stderr, "control hardware info (%i): %s\n", card, 
		snd_strerror(ret));
	    snd_ctl_close(handle);
	    continue;
	}

	dev = -1;

	while (1) {
	    unsigned int count;
	    if (snd_ctl_pcm_next_device(handle, &dev) < 0) {
		fprintf(stderr, "snd_ctl_pcm_next_device");
	    }

	    if (dev < 0) {
	        break;
	    }

	    snd_pcm_info_set_device(pcminfo, dev);
	    snd_pcm_info_set_subdevice(pcminfo, 0);
	    snd_pcm_info_set_stream(pcminfo, type);
	    if ((ret = snd_ctl_pcm_info(handle, pcminfo)) < 0) {
	        if (ret != -ENOENT) {
	    	    fprintf(stderr, "control digital audio info (%i): %s\n", 
			card, snd_strerror(ret));
		}
		break;
	    }

	    deviceInfo[deviceIndex] = malloc(256);

	    sprintf(deviceInfo[deviceIndex], "plughw:%i,%i  %s [%s], %s [%s]\n",
		    card, dev, 
		    snd_ctl_card_info_get_id(info),
		    snd_ctl_card_info_get_name(info),
		    snd_pcm_info_get_id(pcminfo),
		    snd_pcm_info_get_name(pcminfo));

	    //fprintf(stderr, "%s\n", deviceInfo[deviceIndex]);

	    deviceIndex++;

	    //count = snd_pcm_info_get_subdevices_count(pcminfo);
	    //
	    //printf("  Subdevices: %i/%i\n",
	    //	snd_pcm_info_get_subdevices_avail(pcminfo), count);
	    //
	    //for (idx = 0; idx < (int)count; idx++) {
	    //	snd_pcm_info_set_subdevice(pcminfo, idx);
	    //
	    //	if ((ret = snd_ctl_pcm_info(handle, pcminfo)) < 0) {
	    //	    fprintf(stderr, 
	    //	        "control digital audio playback info (%i): %s", 
	    //		card, snd_strerror(ret));
	    //	} else {
	    //	    printf("  Subdevice #%i: %s\n",
	    //		idx, snd_pcm_info_get_subdevice_name(pcminfo));
	    //	}
	    //}
	}

	snd_ctl_close(handle);
    }

    if (deviceIndex <= 0) {
	deviceInfo[0] = "";
	deviceIndex = 1;
    }

    jobjectArray devices = (jobjectArray) (*env)->NewObjectArray(env, deviceIndex,
	(*env)->FindClass(env, "java/lang/String"), (*
	env)->NewStringUTF(env, ""));

    for (i = 0; i < deviceIndex; i++) {
	(*env)->SetObjectArrayElement(env, devices, i, 
	    (*env)->NewStringUTF(env, deviceInfo[i]));

	free(deviceInfo[i]);
    }

    return devices;
}

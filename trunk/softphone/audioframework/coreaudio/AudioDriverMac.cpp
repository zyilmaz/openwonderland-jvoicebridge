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

#include <jni.h>
#include <pthread.h>

#include "com_sun_mc_softphone_media_coreaudio_AudioDriverMac.h"
#include "AudioDriverMacLocal.h" 

jboolean micStarted = false;
jboolean speakerStarted = false;

/******************************************************************************/
/* native methods */
/******************************************************************************/
JNIEXPORT jobjectArray JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nGetInputDevices (JNIEnv * env, jobject jobj)
{   
    debug("start:getInputDevices\n");
    return getAudioDevices(true, env);
}

/******************************************************************************/
JNIEXPORT jobjectArray JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nGetOutputDevices 
(JNIEnv * env, jobject jobj)
{
    debug("start:getOutputDevices\n");
    return getAudioDevices(false, env);
}

/******************************************************************************/
JNIEXPORT jobject JNICALL
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nGetDefaultOutputDevice 
(JNIEnv * env, jobject jobj)
{
    debug("start:getDefaultOutputDevice\n");
    
    OSStatus                    err = noErr;
    AudioDeviceID               device;
    UInt32                      i_param_size = sizeof(AudioDeviceID);
       
    /* get the default output device id */
    err = AudioHardwareGetProperty(kAudioHardwarePropertyDefaultOutputDevice,
                                   &i_param_size,
                                   &device);   
    checkStatus(err);
    
    debug("end:getDefaultOutputDevice\n");
    return getAudioDevice(device, false, env);                       
}

/******************************************************************************/
JNIEXPORT jobject JNICALL
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nGetDefaultInputDevice 
(JNIEnv * env, jobject jobj)
{
    debug("start:getDefaultInputDevice\n");
    
    OSStatus        err = noErr;
    AudioDeviceID   device;
    UInt32          i_param_size = sizeof(AudioDeviceID);
    
    /* get the default input device id */
    err = AudioHardwareGetProperty(kAudioHardwarePropertyDefaultInputDevice,
                                   &i_param_size,
                                   &device);
    checkStatus(err);
    
    debug("end:getDefaultInputDevice\n");
    return getAudioDevice(device, true, env); 
}

/******************************************************************************/
/* this will return an even number of floats in the array, in the form of:
 *     minA, maxA, minB, maxB, ...
 * which will then be processed on the Java side
 */
JNIEXPORT jfloatArray JNICALL
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nGetSupportedSampleRates 
(JNIEnv * env, jobject jobj, jint id, jboolean input)
{
    debug("start:getSupportedSampleRates\n");
    
    jfloatArray         ret;
    OSStatus            err = noErr;
    AudioDeviceID       device = (AudioDeviceID)id;
    UInt32              i_param_size;
    AudioValueRange*    p_ranges;
    
    /* get the size of the result */
    err = AudioDeviceGetPropertyInfo(device, 0, input,
                    kAudioDevicePropertyAvailableNominalSampleRates,
                    &i_param_size, NULL);
    checkStatus(err);
    
    /* allocate space for result */
    if (p_ranges != NULL) {
	free(p_ranges);
    }

    p_ranges = (AudioValueRange*) malloc (i_param_size);
    
    err = AudioDeviceGetProperty(device, 0, input,
                    kAudioDevicePropertyAvailableNominalSampleRates,
                    &i_param_size, (void*)p_ranges);
    checkStatus(err);
               
    /* Get the necessary values from the AudioValueRanges */
    int numRates = i_param_size / sizeof(AudioValueRange);
    ret = (jfloatArray)env->NewFloatArray(numRates*2);
    jfloat *body = env->GetFloatArrayElements(ret, 0);
    
    for (int i = 0; i < numRates; i++) {
        body[i*2]     = p_ranges[i].mMinimum;
        body[i*2 + 1] = p_ranges[i].mMaximum;
    }     
    env->ReleaseFloatArrayElements(ret, body, 0);
    
    debug("end:getSupportedSampleRates\n");
    return ret;
}
  
/******************************************************************************/
JNIEXPORT void JNICALL Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nStart 
(JNIEnv * env, jobject jobj, 
 
 jfloat speakerRate, jint speakerChannels, jint speakerBytesPerPacket, 
 jint speakerFramesPerPacket, jint speakerBytesPerFrame, 
 jint speakerBitsPerChannel,
 
 jfloat micRate, jint micChannels, jint micBytesPerPacket, 
 jint micFramesPerPacket, jint micBytesPerFrame, jint micBitsPerChannel)
{
    debug("start:start device\n");

    if (micStarted || speakerStarted) {
	printf("nStart called but already started!\n");
	return;
    }

    OSStatus        err = noErr;
    
    /* make the audio Speaker description manually from the values passed in */
    inSpeakerDescr.mSampleRate = speakerRate;
    inSpeakerDescr.mChannelsPerFrame = speakerChannels;
    inSpeakerDescr.mBytesPerPacket = speakerBytesPerPacket;
    inSpeakerDescr.mFramesPerPacket = speakerFramesPerPacket;
    inSpeakerDescr.mBytesPerFrame = speakerBytesPerFrame;
    inSpeakerDescr.mBitsPerChannel = speakerBitsPerChannel;
    inSpeakerDescr.mFormatID = kAudioFormatLinearPCM;
    inSpeakerDescr.mFormatFlags = kAudioFormatFlagIsBigEndian |
        kAudioFormatFlagIsSignedInteger | 
        kAudioFormatFlagIsPacked;
    speakerMaxPacketSize = speakerBytesPerPacket;
    
    /* make the audio Mic description manually from the values passed in */
    outMicDescr.mSampleRate = micRate;
    outMicDescr.mChannelsPerFrame = micChannels;
    outMicDescr.mBytesPerPacket = micBytesPerPacket;
    outMicDescr.mFramesPerPacket = micFramesPerPacket;
    outMicDescr.mBytesPerFrame = micBytesPerFrame;
    outMicDescr.mBitsPerChannel = micBitsPerChannel;
    outMicDescr.mFormatID = kAudioFormatLinearPCM;
    outMicDescr.mFormatFlags = kAudioFormatFlagIsBigEndian |
        kAudioFormatFlagIsSignedInteger | 
        kAudioFormatFlagIsPacked;
    micMaxPacketSize = micBytesPerPacket;
    
    /* setup the audio units and audio graphs necessary */
    setupAudioUnits(); 
    
    /* get the desired formats of the hardware devices */
    err = MatchAUFormats(&outSpeakerDescr, &inMicDescr, 0);
    checkStatus(err);
    
    printFormatDescriptions();
    
    /* create the format converters */
    err = MakeAUConverter(&speakerConverter, &inSpeakerDescr, &outSpeakerDescr);
    checkStatus(err);
    err = MakeAUConverter(&micConverter, &inMicDescr, &outMicDescr);
    checkStatus(err);
    
    /* create the ByteBufferLists for the input side */
    setupAudioBufferLists();
    
    /* setup the callbacks to provide data to the converters */
    setupCallbacks(true);
    
    /* have to wait until after the callback setup to call this */
    verify_noerr(AudioUnitInitialize(inputUnit));
    
    /* start the audio units, graphs */
    err = AudioOutputUnitStart(inputUnit);
    checkStatus(err); 
    err = AUGraphStart(outputGraph);
    checkStatus(err);
        
    printf("setting micStarted and speakerStarted to true\n");

    micStarted = true;
    speakerStarted = true;

    debug("end:start device\n");
}

/******************************************************************************/
JNIEXPORT void JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nStop (JNIEnv * env, jobject jobj)
{
    OSStatus                    err = noErr;

    debug("start:stop device\n");
    
    printf("nStop called\n");

    if (micStarted == false || speakerStarted == false) {
	printf("nStop called but not started!\n");
	return;
    }

    //err =  AudioUnitReset(outputUnit,
    //                      kAudioUnitScope_Output,
    //                      0); // output element
    //
    //checkStatus(err);

    pthread_mutex_lock(&speakerProcLock);

        printf("setting speakerStarted to false\n");
        speakerStarted = false;

        setupSpeakerCallback(false);

    pthread_mutex_unlock(&speakerProcLock);

    AudioOutputUnitStop(outputUnit);

    AudioUnitUninitialize (outputUnit);
    AUGraphUninitialize(outputGraph);
    CloseComponent(outputUnit);
    
    err =  AudioUnitReset(inputUnit,
                          kAudioUnitScope_Input,
                          1); // input element

    checkStatus(err);

    pthread_mutex_lock(&micProcLock);

        printf("setting micStarted to false\n");
        micStarted = false;

        setupMicrophoneCallback(false);

    pthread_mutex_unlock(&micProcLock);

    AudioOutputUnitStop(inputUnit);
    AudioUnitUninitialize (inputUnit);

    CloseComponent(inputUnit);
        
    debug("end:stop device\n");
}

/******************************************************************************/
JNIEXPORT jint JNICALL Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nWriteSpeaker 
(JNIEnv * env, jobject jobj)
{
    return 1;
}

/******************************************************************************/
JNIEXPORT jint JNICALL Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nReadMic 
(JNIEnv * env, jobject jobj, jint bytes)
{    
    if (!micStarted) {
	return 0;
    }

    /* make the call for the converter to fill the complex buffer */
    OSStatus err = noErr;
    void *inInputDataProcUserData = NULL;
    AudioStreamPacketDescription* outPacketDescription = NULL;
    
    pthread_mutex_lock(&readMicLock);
    
    int available = micBufferWritePosition - micBufferReadPosition;
    while (available < bytes) {
        /* Do the converting */
        
        UInt32 inNumFrames = bytes / outMicDescr.mBytesPerFrame;
        err = AudioConverterFillComplexBuffer(micConverter, 
                                              ACComplexInputProcMic,
                                              inInputDataProcUserData, 
                                              &inNumFrames,
                                              converted, 
                                              outPacketDescription);
        if (err == 'insz') {
            debug("kAudioConverterErr_InvalidInputSize error\n");
        } else if (err == kAudioConverterErr_NoDataNow) {
            debug("no data to get\n");
        } else {
            checkStatus(err);
        }

        /* now we need to take the data from converted, and place it in the java
        * ByteBuffer 
        */

        /* lock up while mucking with the buffer */
        debugThread("nwml ");
        pthread_mutex_lock(&micBufferLock);
            /* do the compacting if necessary */
            int used = micBufferWritePosition - micBufferReadPosition;
            int length = converted->mBuffers[0].mDataByteSize;

            /* no chance, even if we compact, so just get out */
            if (micBufferCapacity - used < length) {
                debugThread("nwrml ");
                pthread_mutex_unlock(&micBufferLock);
                debugThread("nrml\n");
            } else {
                /* we will have enough room, but only if we compact it */
                if (micBufferCapacity - micBufferWritePosition < length || 
                    micBufferWritePosition > micBufferCapacity) 
                {
                    /* manually compact, then set the pointers appropriately */
                    memmove(nativeMicBuffer, 
                            &nativeMicBuffer[micBufferReadPosition], 
                            used);

                    micBufferReadPosition = 0;
                    micBufferWritePosition = used;
                    debug("\t\t\t\t\t\t\t\t\t\t\tcompacting mic buffer\n");
                }

                /* do the writing */
                memcpy(&nativeMicBuffer[micBufferWritePosition], 
                       converted->mBuffers[0].mData, length);
                micBufferWritePosition += length;
            }
            
        debugThread("nwrml ");
        pthread_mutex_unlock(&micBufferLock);
        debugThread("nrml\n");
        
        available = micBufferWritePosition - micBufferReadPosition;
        if (available < bytes) {
            pthread_cond_wait(&readMicCond, &readMicLock);
        }
        available = micBufferWritePosition - micBufferReadPosition;
    }
    pthread_mutex_unlock(&readMicLock);
    return 1;
}

/******************************************************************************/
JNIEXPORT void JNICALL Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nInitializeBuffers
(JNIEnv * env, jobject jobj, jobject speakerBuffer, jobject micBuffer) 
{
    pthread_mutex_init(&speakerBufferLock, NULL);
    pthread_mutex_init(&micBufferLock, NULL);
    pthread_mutex_init(&localMicBufferLock, NULL);
    pthread_cond_init(&readMicCond, NULL);
    pthread_mutex_init(&readMicLock, NULL);
    pthread_mutex_init(&speakerProcLock, NULL);
    pthread_mutex_init(&micProcLock, NULL);
    
    audioDriverObject = env->NewGlobalRef(jobj);
    nativeSpeakerBufferObject = env->NewGlobalRef(speakerBuffer);
    nativeMicBufferObject = env->NewGlobalRef(micBuffer);
    
    nativeSpeakerBuffer = (char*)env->GetDirectBufferAddress(speakerBuffer);
    nativeMicBuffer = (char*)env->GetDirectBufferAddress(micBuffer);
    
    speakerBufferCapacity = env->GetDirectBufferCapacity(nativeSpeakerBufferObject);
    micBufferCapacity = env->GetDirectBufferCapacity(nativeMicBufferObject);
    
    speakerBufferWritePosition = 0;
    micBufferWritePosition = 0;
    speakerBufferReadPosition = 0;
    micBufferReadPosition = 0;
    
    localMicBufferCapacity = 100000; // fixme
    localMicBufferPosition = 0;

    if (localMicBuffer != NULL) {
	free(localMicBuffer);
    }

    localMicBuffer = (char*) malloc (localMicBufferCapacity);
    
    if (localSpeakerBuffer != NULL) {
	free(localSpeakerBuffer);
    }

    localSpeakerBuffer = (char *) malloc (localMicBufferCapacity); 

    if (tmpMicBuffer != NULL) {
	free(tmpMicBuffer);
    }

    tmpMicBuffer = (char*) malloc (localMicBufferCapacity);
}

/******************************************************************************/
JNIEXPORT void JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nAcquireSpeakerBufferLock
(JNIEnv * env, jobject jobj) {
    debugThread("jwl ");
    pthread_mutex_lock(&speakerBufferLock);
    debugThread("jhl ");
}

/******************************************************************************/
JNIEXPORT void JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nReleaseSpeakerBufferLock 
(JNIEnv * env, jobject jobj) {
    debugThread("jwrl ");
    pthread_mutex_unlock(&speakerBufferLock);
    debugThread("jrl\n");
}

/******************************************************************************/
JNIEXPORT jint JNICALL
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nGetSpeakerBufferWritePosition
(JNIEnv * env, jobject jobj) {
    return speakerBufferWritePosition;
}

/******************************************************************************/
JNIEXPORT jint JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nGetSpeakerBufferReadPosition
(JNIEnv * env, jobject jobj) {
    return speakerBufferReadPosition;
}

/******************************************************************************/
JNIEXPORT void JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nSetSpeakerBufferWritePosition
(JNIEnv * env, jobject jobj, jint value) {
    speakerBufferWritePosition = value;
}

/******************************************************************************/
JNIEXPORT void JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nSetSpeakerBufferReadPosition
(JNIEnv * env, jobject jobj, jint value) {
    speakerBufferReadPosition = value;
}

/******************************************************************************/
JNIEXPORT void JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nAcquireMicBufferLock
(JNIEnv * env, jobject jobj) {
    debugThread("jwl ");
    pthread_mutex_lock(&micBufferLock);
    debugThread("jhl ");
}

/******************************************************************************/
JNIEXPORT void JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nReleaseMicBufferLock 
(JNIEnv * env, jobject jobj) {
    debugThread("jwrl ");
    pthread_mutex_unlock(&micBufferLock);
    debugThread("jrl\n");
}

/******************************************************************************/
JNIEXPORT jint JNICALL
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nGetMicBufferWritePosition
(JNIEnv * env, jobject jobj) {
    return micBufferWritePosition;
}

/******************************************************************************/
JNIEXPORT jint JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nGetMicBufferReadPosition
(JNIEnv * env, jobject jobj) {
    return micBufferReadPosition;
}

/******************************************************************************/
JNIEXPORT void JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nSetMicBufferWritePosition
(JNIEnv * env, jobject jobj, jint value) {
    micBufferWritePosition = value;
}

/******************************************************************************/
JNIEXPORT void JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nSetMicBufferReadPosition
(JNIEnv * env, jobject jobj, jint value) {
    micBufferReadPosition = value;
}

/******************************************************************************/
JNIEXPORT jboolean JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nSetOutputDevice
(JNIEnv * env, jobject jobj, jint id) {
    setOutputDeviceAsCurrent(id);
    return true;
}

/******************************************************************************/
JNIEXPORT jboolean JNICALL 
Java_com_sun_mc_softphone_media_coreaudio_AudioDriverMac_nSetInputDevice
(JNIEnv * env, jobject jobj, jint id) {
    setInputDeviceAsCurrent(id);
    return true;
}

/******************************************************************************/
/* local function implementations */
/******************************************************************************/
/* get the devices connected to this computer, if the parameter is true, we
 * look for the input devices, if the parameter is false, we get the output
 * devices
 */
jobjectArray getAudioDevices(bool input, JNIEnv* env)
{
    debug("start:getAudioDevices\n");
    
    jobjectArray                ret;
    OSStatus                    err = noErr;
    UInt32                      i_param_size;
    AudioDeviceID*              p_devices = NULL;
    
    /* get the list of device IDs */
    err = AudioHardwareGetPropertyInfo(kAudioHardwarePropertyDevices,
                                       &i_param_size,
                                       NULL);
    checkStatus(err);
    
    /* Allocate array for devices */
    p_devices = (AudioDeviceID*) malloc (i_param_size);
    
    /* Populate the array with the devices */
    err = AudioHardwareGetProperty(kAudioHardwarePropertyDevices,
                                   &i_param_size,
                                   (void*)p_devices);
    checkStatus(err);

    /* Get something from each of the devices */
    int numDevices = i_param_size / sizeof(AudioDeviceID);
    jclass adc = env->FindClass("com/sun/mc/softphone/media/coreaudio/AudioDevice");
    jmethodID mid = env->GetMethodID(adc, "<init>", "()V");
    
    /* allocate the array to return */
    ret = (jobjectArray)env->NewObjectArray(numDevices, adc,
                                            env->NewObject(adc, mid));
    
    /* put the devices in the array */
    for (int i = 0; i < numDevices; i++) {
        env->SetObjectArrayElement(ret, i, getAudioDevice(p_devices[i], 
                                   input, env));
    }

    ret = (jobjectArray) env->NewObjectArray(numDevices,
        env->FindClass("java/lang/String"), env->NewStringUTF(""));

    int i;

    char s[100];

    for (i = 0; i < numDevices; i++) {
	sprintf(s, "%d", p_devices[i]);
        env->SetObjectArrayElement(ret, i, env->NewStringUTF(s));
    }

    free(p_devices);

    debug("end:getAudioDevices\n");
    return ret;    
}

/******************************************************************************/
/* make a AudioDevice object for the AudioDeviceID given
 */
jobject getAudioDevice(AudioDeviceID adid, bool input, JNIEnv* env) 
{
    debug("start:getAudioDevice\n");
    
    UInt32                      i_param_size;
    OSStatus                    err = noErr;
    char                        psz_name[1024];
    UInt32                      deviceBufferSize;
    AudioStreamBasicDescription deviceFormat;
    
    /* get the class */
    jclass adc = env->FindClass("com/sun/mc/softphone/media/coreaudio/AudioDevice");
    jmethodID mid = env->GetMethodID(adc, "<init>", "()V");
    if (adc == 0 || mid == 0) {
        printf("Didn't get class or method: adc %i, mid %i\n", 
            (int)adc, (int)mid);
    }
    
    /* get the field ids */
    jfieldID name =          env->GetFieldID(adc, "name", "Ljava/lang/String;");
    jfieldID id =            env->GetFieldID(adc, "id", "I");
    jfieldID bufferSize =    env->GetFieldID(adc, "bufferSize", "I");
    jfieldID sampleRate =    env->GetFieldID(adc, "sampleRate", "F");
    jfieldID bytesPerPacket = env->GetFieldID(adc, "bytesPerPacket", "I");
    jfieldID framesPerPacket = env->GetFieldID(adc, "framesPerPacket", "I");
    jfieldID channelsPerFrame = env->GetFieldID(adc, "channelsPerFrame", "I");
    jfieldID bitsPerChannel = env->GetFieldID(adc, "bitsPerChannel", "I");
      
    // get the name of the device
    i_param_size = sizeof psz_name;
    err = AudioDeviceGetProperty(adid, 0, input,
                                 kAudioDevicePropertyDeviceName,
                                 &i_param_size, psz_name);
    checkStatus(err);    
    
    // get the buffersize that the device uses for IO
    i_param_size = sizeof(deviceBufferSize);
    err = AudioDeviceGetProperty(adid, 0, input, 
                                 kAudioDevicePropertyBufferSize, 
                                 &i_param_size, &deviceBufferSize);
    checkStatus(err);
       
    // get a description of the data formreadAt used by the device
    i_param_size = sizeof(deviceFormat);
    err = AudioDeviceGetProperty(adid, 0, input, 
                                 kAudioDevicePropertyStreamFormat, 
                                 &i_param_size, &deviceFormat);
    // not checking for the error on this, because we will get one
    // if we look for input on an output device and vice versa
    // checkStatus(err);

    /* create the AudioDevice */
    jobject ins = env->NewObject(adc, mid);

    /* set the fields of the AudioDevice */
    env->SetObjectField(ins, name, env->NewStringUTF(psz_name));
    env->SetIntField(ins, id, adid);
    env->SetIntField(ins, bufferSize, deviceBufferSize);
    env->SetFloatField(ins, sampleRate, deviceFormat.mSampleRate);
    env->SetIntField(ins, bytesPerPacket, deviceFormat.mBytesPerPacket);
    env->SetIntField(ins, framesPerPacket, deviceFormat.mFramesPerPacket);
    env->SetIntField(ins, channelsPerFrame, deviceFormat.mChannelsPerFrame);
    env->SetIntField(ins, bitsPerChannel, deviceFormat.mBitsPerChannel);

    debug("end:getAudioDevice\n");
    return ins;
}

/******************************************************************************/
void setupInputUnit()
{
    OSStatus err;
    Component comp;
    ComponentDescription desc;

    /* Set up the input audio unit */
    desc.componentType = kAudioUnitType_Output;
    desc.componentSubType = kAudioUnitSubType_HALOutput;
    desc.componentManufacturer = kAudioUnitManufacturer_Apple;
	desc.componentFlags = 0;
	desc.componentFlagsMask = 0;
    
	comp = FindNextComponent(NULL, &desc);
    if (comp == NULL) exit (-1);
    
	verify_noerr(OpenAComponent(comp, &inputUnit));  
    
    /* We must enable the Audio Unit for input and disable output BEFORE 
        setting the AUHAL's current device.
        */
	UInt32 enableIO;
    
    /* Enable input on the input unit */
	enableIO = 1;
	err =  AudioUnitSetProperty(inputUnit,
	   			    kAudioOutputUnitProperty_EnableIO,
				    kAudioUnitScope_Input,
				    1, // input element
				    &enableIO,
				    sizeof(enableIO));

	checkStatus(err);
	
	/* disable Output on the input unit */
	enableIO = 0;
	err = AudioUnitSetProperty(inputUnit,
                               kAudioOutputUnitProperty_EnableIO,
                               kAudioUnitScope_Output,
                               0,   //output element
                               &enableIO,
                               sizeof(enableIO));
    checkStatus(err);
}

/******************************************************************************/
void setupOutputUnit()
{   
    OSStatus err;
    
    NewAUGraph(&outputGraph);
    AUGraphOpen(outputGraph);
    
    makeOutputGraph();
    
    err = AUGraphInitialize(outputGraph);
    checkStatus(err);
}

/******************************************************************************/
void makeOutputGraph()
{
	ComponentDescription outDesc, reverbDesc;
    
	outDesc.componentType = kAudioUnitType_Output;
	outDesc.componentSubType = kAudioUnitSubType_DefaultOutput;
	outDesc.componentManufacturer = kAudioUnitManufacturer_Apple;
	outDesc.componentFlags = 0;
	outDesc.componentFlagsMask = 0;
	
	AUGraphNewNode(outputGraph, &outDesc, 0, NULL, &outputNode);
	AUGraphGetNodeInfo(outputGraph, outputNode, NULL, NULL, NULL, &outputUnit);   
	
    bool reverb = false;
    if (reverb)
    {
        /* testing addition of additional audio units */
        reverbDesc.componentType = kAudioUnitType_Effect;
        reverbDesc.componentSubType = kAudioUnitSubType_MatrixReverb;
        reverbDesc.componentManufacturer = kAudioUnitManufacturer_Apple;
        reverbDesc.componentFlags = 0;
        reverbDesc.componentFlagsMask = 0;
        
        AUGraphNewNode(outputGraph, &reverbDesc, 0, NULL, &reverbNode);
        AUGraphGetNodeInfo(outputGraph, reverbNode, NULL, NULL, NULL, &reverbUnit); 
        
        //connect nodes
        AUGraphConnectNodeInput(outputGraph, reverbNode, 0, outputNode, 0);
    }
    
	AUGraphUpdate(outputGraph, NULL);
}

/******************************************************************************/
void setupAudioUnits() 
{
    OSStatus err;
    
    setupInputUnit();
    err = setInputDeviceAsCurrent(inputDevice);
    checkStatus(err);
    
    setupOutputUnit();
    err = setOutputDeviceAsCurrent(outputDevice);
    checkStatus(err);
}

/******************************************************************************/
void setupAudioBufferLists()
{
    OSStatus err = noErr;
    UInt32 bufferSizeFrames, bufferSizeBytes, propsize;
    AudioStreamBasicDescription asbd;
    
    //Allocate a local buffer for the input microphone
    if (localMicBuffer != NULL) {
	free(localMicBuffer);
    }

    localMicBuffer = (char*) malloc (localMicBufferCapacity);
    localMicBufferPosition = 0;
    
    /* It would be nice to be able to set the size of this buffer to something
     * bigger so we get less frequent calls to the callback and therefore
     * better sounding stuff coming out of the AudioConverter */
    
    //Get the size of the IO buffer(s)
	UInt32 propertySize = sizeof(bufferSizeFrames);
    
    err = AudioUnitGetProperty(inputUnit, kAudioDevicePropertyBufferFrameSize,
                               kAudioUnitScope_Global, 0, &bufferSizeFrames,
                               &propertySize);
    checkStatus(err);
    
    //Get the Stream Format (client side)
	propertySize = sizeof(asbd);
	err = AudioUnitGetProperty(inputUnit, kAudioUnitProperty_StreamFormat,
                               kAudioUnitScope_Output, 1, &asbd, &propertySize);
    checkStatus(err);
    
    bufferSizeBytes = bufferSizeFrames * asbd.mBytesPerFrame;
    propsize = offsetof(AudioBufferList, mBuffers[0]) + 
                        (sizeof(AudioBuffer) * asbd.mChannelsPerFrame);
    
    //malloc buffer lists
	if (rendered != NULL) {
	    for(UInt32 i =0; i< rendered->mNumberBuffers ; i++) {
		free(rendered->mBuffers[i].mData);
	    }

	    free(rendered);
	}

	rendered = (AudioBufferList*) malloc (propsize);
	rendered->mNumberBuffers = 1;
	
	//pre-malloc buffers for AudioBufferLists
	for(UInt32 i =0; i< rendered->mNumberBuffers ; i++) {
		rendered->mBuffers[i].mNumberChannels = 1;
		rendered->mBuffers[i].mDataByteSize = bufferSizeBytes;
		rendered->mBuffers[i].mData = malloc(bufferSizeBytes);
	}
    
    //malloc buffer lists
	if (converted != NULL) {
    	    for(UInt32 i =0; i< converted->mNumberBuffers ; i++) {
		free(converted->mBuffers[i].mData);
	    }
	    free(converted);
	}

	converted = (AudioBufferList*) malloc (propsize);
	converted->mNumberBuffers = 1;
	
	//pre-malloc buffers for AudioBufferLists
	bufferSizeBytes = bufferSizeFrames * outMicDescr.mBytesPerFrame;
        for(UInt32 i =0; i< converted->mNumberBuffers ; i++) {
		converted->mBuffers[i].mNumberChannels = 1;
		converted->mBuffers[i].mDataByteSize = bufferSizeBytes;
		converted->mBuffers[i].mData = malloc(bufferSizeBytes);
	}
}

/******************************************************************************/
void resetAudioBufferLists()
{
    OSStatus err = noErr;
    UInt32 bufferSizeFrames, bufferSizeBytes;
    
    //Get the size of the IO buffer(s)
	UInt32 propertySize = sizeof(bufferSizeFrames);
	err = AudioUnitGetProperty(inputUnit, kAudioDevicePropertyBufferFrameSize,
                               kAudioUnitScope_Global, 0, &bufferSizeFrames,
                               &propertySize);
    checkStatus(err);
    
    bufferSizeBytes = bufferSizeFrames * inMicDescr.mBytesPerFrame;
    
	rendered->mNumberBuffers = 1;
	for(UInt32 i =0; i< rendered->mNumberBuffers ; i++) {
		rendered->mBuffers[i].mDataByteSize = bufferSizeBytes;
	}
    
	converted->mNumberBuffers = 1;
	bufferSizeBytes = bufferSizeFrames * outMicDescr.mBytesPerFrame;
    for(UInt32 i =0; i< converted->mNumberBuffers ; i++) {
		converted->mBuffers[i].mDataByteSize = bufferSizeBytes;
	}
}


/******************************************************************************/
OSStatus setOutputDeviceAsCurrent(AudioDeviceID out)
{
    UInt32 size = sizeof(AudioDeviceID);
    OSStatus err = noErr;
	
	if (out == kAudioDeviceUnknown) {
		err = AudioHardwareGetProperty(kAudioHardwarePropertyDefaultOutputDevice,
									   &size, &out);
        checkStatus(err);
	}
    outputDevice = out;
	
	/* Set this device to the Output Unit */
    err = AudioUnitSetProperty(outputUnit,
                               kAudioOutputUnitProperty_CurrentDevice, 
                               kAudioUnitScope_Global, 
                               0, 
                               &outputDevice, 
                               sizeof(outputDevice));
    /* there will be an error here if we haven't done the input and output
     * enabling yet */
    //checkStatus(err);
	return err;
}

/******************************************************************************/
OSStatus setInputDeviceAsCurrent(AudioDeviceID in)
{
    UInt32 size = sizeof(AudioDeviceID);
    OSStatus err = noErr;
	
	if (in == kAudioDeviceUnknown) {  
		err = AudioHardwareGetProperty(kAudioHardwarePropertyDefaultInputDevice,
									   &size, &in);
		checkStatus(err);
	}
	inputDevice = in;
	
	/* Set the Current Device to the AUHAL */
    err = AudioUnitSetProperty(inputUnit,
                               kAudioOutputUnitProperty_CurrentDevice, 
                               kAudioUnitScope_Global, 
                               0,
                               &inputDevice, 
                               sizeof(inputDevice));
	/* there will be an error here if we haven't done the input and output
     * enabling yet */
    //checkStatus(err);
	return err;
}

/******************************************************************************/
OSStatus MatchAUFormats (AudioStreamBasicDescription *outputDesc,
                         AudioStreamBasicDescription *inputDesc,
                         UInt32 theInputBus)
{
	UInt32 i_param_size = sizeof (AudioStreamBasicDescription);
    memset(outputDesc, 0, i_param_size);
    memset(inputDesc, 0, i_param_size);
    
    Boolean outWritable;
    OSStatus result = noErr;                            
    
    /* For the output unit... */
    //Gets the size of the Stream Format Property and if it is writable
    AudioUnitGetPropertyInfo(outputUnit, kAudioUnitProperty_StreamFormat,
                             kAudioUnitScope_Output, 0, &i_param_size, 
                             &outWritable);
                       

    //Get the current stream format of the output
    result = AudioUnitGetProperty (outputUnit, 
                                   kAudioUnitProperty_StreamFormat,
                                   kAudioUnitScope_Output, 0, outputDesc,
                                   &i_param_size);
    checkStatus(result);

    //Set the stream format of the output to match the input
    result = AudioUnitSetProperty (outputUnit, 
                                   kAudioUnitProperty_StreamFormat,
                                   kAudioUnitScope_Input, theInputBus, 
                                   outputDesc, i_param_size);

    /* For the input unit... */
    AudioUnitGetPropertyInfo(inputUnit, kAudioUnitProperty_StreamFormat,
                             kAudioUnitScope_Input, 1, &i_param_size, 
                             &outWritable);
    
    //Get the current stream format of the output
    result = AudioUnitGetProperty (inputUnit, 
                                   kAudioUnitProperty_StreamFormat,
                                   kAudioUnitScope_Input, 1, inputDesc,
                                   &i_param_size);
    checkStatus(result);
             
    //Set the stream format of the input to match the data
    result = AudioUnitSetProperty (inputUnit, 
                                   kAudioUnitProperty_StreamFormat,
                                   kAudioUnitScope_Output, 1,
                                   inputDesc, i_param_size);
    
    /* We get an error here, but not sure if it is necessary... */
    checkStatus(result);
    
	return result;
}

/******************************************************************************/
OSStatus getFileInfo(FSRef *fileRef, AudioFileID *fileID,
                     AudioStreamBasicDescription *fileASBD, 
                     const char *fileName)
{
    OSStatus err= noErr;
    UInt32 size;
    
    FSPathMakeRef ((const UInt8 *)fileName, fileRef, 0);
    err = AudioFileOpen(fileRef, fsRdPerm, 0, fileID);
    checkStatus(err);
    
    size = sizeof(AudioStreamBasicDescription);
    memset(fileASBD, 0, size);
    err = AudioFileGetProperty(*fileID, kAudioFilePropertyDataFormat,
                               &size, fileASBD); 
    checkStatus(err);
   
    //Get total packet count, byte count, and max packet size
    //Theses values will be used later when grabbing data from the audio file
    size = sizeof(packetCount);
    err = AudioFileGetProperty(*fileID, kAudioFilePropertyAudioDataPacketCount,
                               &size, &packetCount);
    checkStatus(err);
    
    size = sizeof(byteCount);
    err = AudioFileGetProperty(*fileID, kAudioFilePropertyAudioDataByteCount, 
                               &size, &byteCount);
    checkStatus(err);
    
    size = sizeof(speakerMaxPacketSize);
    err = AudioFileGetProperty(*fileID, kAudioFilePropertyMaximumPacketSize, 
                               &size, &speakerMaxPacketSize);
    checkStatus(err);
    
    return err;
}

/******************************************************************************/
OSStatus MakeAUConverter(AudioConverterRef *conv,
                         AudioStreamBasicDescription *inASBD,
                         AudioStreamBasicDescription *outASBD)
{
    OSStatus err;
    
    err = AudioConverterNew(inASBD, outASBD , conv);
    checkStatus(err);
    return err;
}

/******************************************************************************/
void setupCallbacks(jboolean initialize)
{  
    setupSpeakerCallback(initialize);
    setupMicrophoneCallback(initialize);
}

void setupSpeakerCallback(jboolean initialize)
{  
    OSStatus err= noErr;
    
    AURenderCallbackStruct speakerCallback;
    
    if (initialize) {
        speakerCallback.inputProc = speakerProc;
    } else {
        speakerCallback.inputProc = NULL;
    }

    speakerCallback.inputProcRefCon = 0;

    //Sets the callback for the Output Audio Unit
    err = AudioUnitSetProperty (outputUnit, 
                                kAudioUnitProperty_SetRenderCallback, 
                                kAudioUnitScope_Input, 0, &speakerCallback, 
                                sizeof(AURenderCallbackStruct));
    checkStatus(err);                            
}

void setupMicrophoneCallback(jboolean initialize)
{  
    OSStatus err= noErr;
    
    AURenderCallbackStruct micCallback;

    if (initialize) {
        micCallback.inputProc = micProc;
    } else {
        micCallback.inputProc = NULL;
    }

    micCallback.inputProcRefCon = 0;
    
    //Sets the callback for the Input Audio Unit
    err = AudioUnitSetProperty (inputUnit, 
                                kAudioOutputUnitProperty_SetInputCallback,
                                kAudioUnitScope_Global, 0, &micCallback, 
                                sizeof(AURenderCallbackStruct));
    checkStatus(err);
}

/******************************************************************************/
OSStatus speakerProc(void 			*inRefCon, 
                    AudioUnitRenderActionFlags	*inActionFlags,
                    const AudioTimeStamp 	*inTimeStamp, 
                    UInt32                       inBusNumber,
                    UInt32                       inNumFrames, 
                    AudioBufferList             *ioData)
{
    //printf("speakerProc 1\n");

    pthread_mutex_lock(&speakerProcLock);

        if (!speakerStarted) {
	    printf("speakerProc called but not started!\n");
            pthread_mutex_unlock(&speakerProcLock);
	    return 1001;
        }


    OSStatus err = noErr;
    void *inInputDataProcUserData = NULL;
    AudioStreamPacketDescription* outPacketDescription = NULL;
    
    err = AudioConverterFillComplexBuffer(speakerConverter,
                                          ACComplexInputProcSpeaker,
                                          inInputDataProcUserData, &inNumFrames, 
                                          ioData, outPacketDescription);
    
    if (err == kAudioConverterErr_InvalidInputSize) {
        debug("kAudioConverterErr_InvalidInputSize error\n");
    } else if (err == kAudioConverterErr_NoDataNow) {
        debug("no data to get\n");
    } else {
        checkStatus(err);
    }

    pthread_mutex_unlock(&speakerProcLock);
    //printf("speakerProc 2\n");
    return err;
}


/******************************************************************************/
OSStatus micProc(void 			    *inRefCon, 
                 AudioUnitRenderActionFlags *inActionFlags,
                 const AudioTimeStamp 	    *inTimeStamp, 
                 UInt32                      inBusNumber,
                 UInt32                      inNumFrames, 
                 AudioBufferList            *ioData)
{    
    debug("mic proc\n");

    //printf("micProc 1\n");
    pthread_mutex_lock(&micProcLock);

        if (!micStarted) {
	    printf("micProc called but not started!\n");
            pthread_mutex_unlock(&micProcLock);
	    return 1002;
        }

    OSStatus err = noErr;
    
    /* fix the buffers */
    resetAudioBufferLists();
    
    /* render the data */
    err= AudioUnitRender(inputUnit, inActionFlags, inTimeStamp, inBusNumber,
                         inNumFrames, rendered);
    checkStatus(err);
         
    pthread_mutex_lock(&readMicLock);
        /* now put this data in the local buffer */
        int length = rendered->mBuffers[0].mDataByteSize;
        if (localMicBufferCapacity - localMicBufferPosition - length > 0) {
            pthread_mutex_lock(&localMicBufferLock);
                memcpy(&localMicBuffer[localMicBufferPosition],
                        rendered->mBuffers[0].mData,
                        length);
                localMicBufferPosition += length;
            pthread_mutex_unlock(&localMicBufferLock);
        } else {
            debug("Dropped inside of micProc\n");
        }
    pthread_mutex_unlock(&readMicLock);
    pthread_cond_signal(&readMicCond);
    
    pthread_mutex_unlock(&micProcLock);
    //printf("micProc 2\n");
    return err;
}

/******************************************************************************/
OSStatus ACComplexInputProcMic (AudioConverterRef inAudioConverter, 
                        UInt32 *ioNumberDataPackets, AudioBufferList *ioData,
                        AudioStreamPacketDescription **outDataPacketDescription,
                        void* inUserData)
{
    OSStatus err = noErr;
    
    if (localMicBufferPosition == 0) {
        *ioNumberDataPackets = 0;
        return kAudioConverterErr_NoDataNow;
    }
        
    /*
    if (tmpMicBuffer != NULL) {
        free(tmpMicBuffer);
        tmpMicBuffer = NULL;
    }
    */
    
    pthread_mutex_lock(&localMicBufferLock);
        int bytesToMove = *ioNumberDataPackets * inMicDescr.mBytesPerPacket;
        if (bytesToMove > localMicBufferPosition) {
            bytesToMove = localMicBufferPosition;
        }
        
        //tmpMicBuffer = (char*) malloc (bytesToMove);
        memcpy(tmpMicBuffer, localMicBuffer, bytesToMove);
        
        /* compact the buffer */
        memcpy(localMicBuffer, &localMicBuffer[bytesToMove], 
               localMicBufferPosition - bytesToMove);
        localMicBufferPosition -= bytesToMove;
    pthread_mutex_unlock(&localMicBufferLock);

    /* put the data in the appropriate buffer */
    ioData->mBuffers[0].mNumberChannels = rendered->mBuffers[0].mNumberChannels;
    ioData->mBuffers[0].mData = tmpMicBuffer;
    ioData->mBuffers[0].mDataByteSize = bytesToMove;
    
    *ioNumberDataPackets = bytesToMove / inMicDescr.mBytesPerPacket;
        
    return err;
}

/******************************************************************************/
OSStatus ACComplexInputProcSpeaker (AudioConverterRef inAudioConverter, 
                        UInt32 *ioNumberDataPackets, AudioBufferList *ioData,
                        AudioStreamPacketDescription **outDataPacketDescription,
                        void* inUserData)
{
    OSStatus    err = noErr;
    int         outChannels = ioData->mNumberBuffers;
    
    // initialize in case of failure
    for (int i = 0; i < outChannels; i++) {
        ioData->mBuffers[i].mData = NULL;			
        ioData->mBuffers[i].mDataByteSize = 0;
    }
    
    // do nothing if there are no packets available
    if (*ioNumberDataPackets)
    {
        /*
        if (localSpeakerBuffer != NULL) {
            free(localSpeakerBuffer);
            localSpeakerBuffer = NULL;
        }
        */
        
        /* lock up while mucking with the buffer */
        debugThread("nwl ");
        pthread_mutex_lock(&speakerBufferLock);
            debugThread("nhl ");
        
            int readPosition = speakerBufferReadPosition;
            int writePosition = speakerBufferWritePosition;
            int remaining = writePosition - readPosition;
            
            if (remaining * speakerMaxPacketSize < *ioNumberDataPackets) {
                *ioNumberDataPackets = remaining * speakerMaxPacketSize;
            }
            
            int toFill = *ioNumberDataPackets * speakerMaxPacketSize;
            //localSpeakerBuffer = (char *) malloc (toFill); 

            if (remaining < toFill) {
                toFill = remaining;
            }
            memcpy(localSpeakerBuffer, &nativeSpeakerBuffer[readPosition], toFill);
            speakerBufferReadPosition += toFill;
        debugThread("nwrl ");
        pthread_mutex_unlock(&speakerBufferLock);
        debugThread("nrl\n");

        /* put the information in the data to be returned */
        for (int i = 0; i < outChannels; i++) {
            ioData->mBuffers[i].mData = localSpeakerBuffer;
            ioData->mBuffers[i].mDataByteSize = toFill;
        }
        
        if (*ioNumberDataPackets == 0) {
            // this signals that we have data coming, just not right now
            err = kAudioConverterErr_NoDataNow; 
        }
    } else {
        // there aren't any more packets to read readAt this time
        for (int i = 0; i < outChannels; i++) {
            ioData->mBuffers[i].mData = NULL;			
            ioData->mBuffers[i].mDataByteSize = 0;
        }
        err = noErr; // this signals that we are at the end of stream
    }
        
    return err;   
}

/******************************************************************************/
void printFormatDescriptions()
{
    /* see how all this stream formatting worked out */
    printf("Input to Speaker:\n");
    PrintStreamDesc(&inSpeakerDescr);
    printf("Output from Speaker:\n");
    PrintStreamDesc(&outSpeakerDescr);
    printf("Input to Microphone:\n");
    PrintStreamDesc(&inMicDescr);
    printf("Output from Microphone:\n");
    PrintStreamDesc(&outMicDescr);
}

/******************************************************************************/
void PrintStreamDesc (AudioStreamBasicDescription *inDesc)
{
	if (!inDesc) {
		printf ("Can't print a NULL desc!\n");
		return;
	}
	
	printf ("- - - - - - - - - - - - - - - - - - - -\n");
	printf ("  Sample Rate:        %f\n", inDesc->mSampleRate);
	printf ("  Format ID:          %s\n", (char*)&inDesc->mFormatID);
	printf ("  Format Flags:       %i\n", (int)inDesc->mFormatFlags);
	printf ("  Bytes per Packet:   %ld\n", inDesc->mBytesPerPacket);
	printf ("  Frames per Packet:  %ld\n", inDesc->mFramesPerPacket);
	printf ("  Bytes per Frame:    %ld\n", inDesc->mBytesPerFrame);
	printf ("  Channels per Frame: %ld\n", inDesc->mChannelsPerFrame);
	printf ("  Bits per Channel:   %ld\n", inDesc->mBitsPerChannel);
	printf ("- - - - - - - - - - - - - - - - - - - -\n");
}

/******************************************************************************/
int getByteBufferLimit(JNIEnv* env, jobject buffer)
{
    jclass cls = env->GetObjectClass(buffer);
    jmethodID mid = env->GetMethodID(cls, "limit", "()I");

    int limit = env->CallIntMethod(buffer, mid);
    return limit;
}

/******************************************************************************/
int getByteBufferPosition(JNIEnv* env, jobject buffer)
{
    jclass cls = env->GetObjectClass(buffer);
    jmethodID mid = env->GetMethodID(cls, "position", "()I");
    
    int position = env->CallIntMethod(buffer, mid);
    return position;
}

/******************************************************************************/
void setByteBufferPosition(JNIEnv* env, jobject buffer, int newPosition)
{
    jclass cls = env->GetObjectClass(buffer);
    jmethodID mid = env->GetMethodID(cls, "position", "(I)Ljava/nio/Buffer;");
    
    env->CallObjectMethod(buffer, mid, newPosition);
}

/******************************************************************************/

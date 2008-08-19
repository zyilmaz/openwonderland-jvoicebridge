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

#ifndef _AUDIO_DRIVER_MAC_LOCAL_H_
#define _AUDIO_DRIVER_MAC_LOCAL_H_

#include <CoreAudio/CoreAudio.h>
#include <CoreAudio/AudioHardware.h>
#include <AudioUnit/AudioUnit.h>
#include <AudioToolbox/AudioConverter.h>
#include <AudioToolbox/AudioFile.h>
#include <AudioToolbox/AUGraph.h>
#include <CoreServices/CoreServices.h>

#define _DEBUG 0
#define _DEBUG_THREADS 0
#define checkStatus( err) \
if(err) {\
    printf("Error: %i %s ->  %s: %d\n", (int)err, (char *)&err,__FILE__, __LINE__);\
        fflush(stdout);\
}

enum
{
	kAudioConverterErr_NoDataNow		= 'w8:)',
    kAudioBufferErr_DropPacket          = 'drp#'
};
/******************************************************************************/
/* local function declarations */
/******************************************************************************/
void debug(char* str) { if (_DEBUG) printf(str); };
void debugThread(char* str) { if (_DEBUG_THREADS) printf(str); };
void PrintStreamDesc (AudioStreamBasicDescription *inDesc);
void printFormatDescriptions();

jobject         getAudioDevice(AudioDeviceID id, bool input, JNIEnv* env); 
jobjectArray    getAudioDevices(bool input, JNIEnv* env);

void            setupAudioUnits();
void            setupInputUnit();
void            setupOutputUnit();
void            makeOutputGraph();
void            setupAudioBufferLists();
void            resetAudioBufferLists();

OSStatus        setOutputDeviceAsCurrent(AudioDeviceID out);
OSStatus        setInputDeviceAsCurrent(AudioDeviceID out);
OSStatus        MatchAUFormats (AudioStreamBasicDescription *outputDesc,
                    AudioStreamBasicDescription *inputDesc,
                    UInt32 theInputBus);
OSStatus        getFileInfo(FSRef *fileRef, AudioFileID *fileID,
                    AudioStreamBasicDescription *fileASBD, 
                    const char *fileName);
OSStatus        MakeAUConverter(AudioConverterRef *conv,
                    AudioStreamBasicDescription *inASBD,
                    AudioStreamBasicDescription *outASBD);
void            setupCallbacks(jboolean initialize);
void            setupSpeakerCallback(jboolean initialize);
void            setupMicrophoneCallback(jboolean initialize);
OSStatus        speakerProc(void *inRefCon, 
                    AudioUnitRenderActionFlags *inActionFlags,
                    const AudioTimeStamp *inTimeStamp, UInt32 inBusNumber,
                    UInt32 inNumFrames, AudioBufferList *ioData);
OSStatus        ACComplexInputProcSpeaker (AudioConverterRef inAudioConverter, 
                    UInt32 *ioNumberDataPackets, AudioBufferList *ioData,
                    AudioStreamPacketDescription **outDataPacketDescription,
                    void* inUserData);

OSStatus        micProc(void *inRefCon, 
                    AudioUnitRenderActionFlags *inActionFlags,
                    const AudioTimeStamp *inTimeStamp, UInt32 inBusNumber,
                    UInt32 inNumFrames, AudioBufferList *ioData);
OSStatus        ACComplexInputProcMic(AudioConverterRef inAudioConverter, 
                    UInt32 *ioNumberDataPackets, AudioBufferList *ioData,
                    AudioStreamPacketDescription **outDataPacketDescription,
                    void* inUserData);
                    
int     getByteBufferLimit(JNIEnv* env, jobject buffer);
int     getByteBufferPosition(JNIEnv* env, jobject buffer);
void    setByteBufferPosition(JNIEnv* env, jobject buffer, int newPosition);

/******************************************************************************/
/* local variable declarations */
/******************************************************************************/
jmethodID                   getReadMid;
jobject                     audioDriverObject;
jclass                      nativeBufferClass;
jclass                      audioDriverClass;

jobject             nativeSpeakerBufferObject, nativeMicBufferObject;
char               *nativeSpeakerBuffer, *nativeMicBuffer;

pthread_mutex_t     speakerBufferLock, micBufferLock, localMicBufferLock;
int                 speakerBufferWritePosition, micBufferWritePosition = 0;
int                 speakerBufferReadPosition, micBufferReadPosition = 0;
int                 speakerBufferCapacity, micBufferCapacity = 0;

pthread_cond_t      readMicCond;
pthread_mutex_t     readMicLock;

pthread_mutex_t	    speakerProcLock;
pthread_mutex_t	    micProcLock;

AudioStreamBasicDescription inSpeakerDescr, outSpeakerDescr;
AudioStreamBasicDescription inMicDescr, outMicDescr;

AUGraph                     outputGraph;
AUNode                      outputNode, reverbNode;
AudioUnit                   outputUnit, inputUnit, reverbUnit;
AudioDeviceID               outputDevice, inputDevice;
AudioConverterRef           speakerConverter, micConverter;
UInt32                      speakerMaxPacketSize, micMaxPacketSize;
char                       *localSpeakerBuffer, *localMicBuffer, *tmpMicBuffer;
int                         localMicBufferPosition, localMicBufferCapacity;
AudioBufferList            *converted, *rendered;

AudioFileID                 audioFileID;

UInt64                      byteCount;
UInt64                      packetCount;



#endif

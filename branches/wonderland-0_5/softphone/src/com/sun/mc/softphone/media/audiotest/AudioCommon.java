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

package com.sun.mc.softphone.media.audiotest;

import java.io.IOException;

import com.sun.mc.softphone.media.AudioFactory;
import com.sun.mc.softphone.media.Microphone;
import com.sun.mc.softphone.media.Speaker;

public class AudioCommon {

    private static AudioFactory audioFactory;

    private static int sampleRate;
    private static int channels;

    public static void initialize(int sampleRate, int channels) {
	AudioCommon.sampleRate = sampleRate;
	AudioCommon.channels = channels;

        try {
            audioFactory = AudioFactory.getInstance();

            audioFactory.initialize(sampleRate, channels,
                sampleRate, channels, getMicrophoneBufferSize(),
                getSpeakerBufferSize());
        } catch (IOException e) {
            System.out.println("Can't get speaker! " + e.getMessage());
            System.exit(1);
	}
    }

    public static int getMicrophoneBufferSize() {
	/*
         * 2 packets
         */
        return 2 * sampleRate * channels * 2 / 50;
    }

    public static int getSpeakerBufferSize() {
	/*
         * 8 packets
         */
        return 8 * sampleRate * channels * 2 / 50;
    }

    public static void listDevices() {
        AudioFactory audioFactory = AudioFactory.getInstance();

        String[] microphones = audioFactory.getMicrophoneList();

        System.out.println("Microphones");

        for (int i = 0; i < microphones.length; i++) {
            System.out.print("  " + microphones[i]);
        }

        String[] speakers = audioFactory.getSpeakerList();

        System.out.println("\nSpeakers");

        for (int i = 0; i < speakers.length; i++) {
            System.out.print("  " + speakers[i]);
        }

        System.out.println("");
    }

    public static Speaker getSpeaker() {
        return audioFactory.getSpeaker();
    }

    public static Microphone getMicrophone() {
        return audioFactory.getMicrophone();
    }
    
}

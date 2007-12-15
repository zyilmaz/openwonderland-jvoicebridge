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

package com.sun.mc.softphone.media.javasound;

import java.util.ArrayList;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import com.sun.mc.softphone.common.Utils;

import com.sun.mc.softphone.media.Microphone;
import com.sun.mc.softphone.media.Speaker;

import com.sun.voip.Logger;

public class GetDataLines {

    public static String[] getMicrophones() {
	ArrayList<String> mics = getDevices(true);

	String[] ret = new String[mics.size()];

	for (int i = 0; i < ret.length; i++) {
	    ret[i] = mics.get(i);
	}

	return ret;
    }

    public static String[] getSpeakers() {
        ArrayList<String> speakers = getDevices(false);

        String[] ret = new String[speakers.size()];

        for (int i = 0; i < ret.length; i++) {
            ret[i] = speakers.get(i);
        }

	return ret;
    }

    private static ArrayList<String> getDevices(boolean isMicrophone) {
	Mixer.Info[] aInfos = AudioSystem.getMixerInfo();

	ArrayList<String> devices = new ArrayList<String>();

        for (int i = 0; i < aInfos.length; i++) {
            try {
                Mixer mixer = AudioSystem.getMixer(aInfos[i]);

                //Logger.println(""+i+": "+aInfos[i].getName()+", "
                //                   +aInfos[i].getVendor()+", "
                //                   +aInfos[i].getVersion()+", "
                //                   +aInfos[i].getDescription());

		if (isMicrophone) {
                    addMicLines(devices, aInfos[i], mixer);
		} else {
		    addSpeakerLines(devices, aInfos[i], mixer);
		}
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

	return devices;
    }

    private static void addMicLines(ArrayList<String> devices, 
	    Mixer.Info mixerInfo, Mixer mixer) throws LineUnavailableException {

	String name = mixerInfo.getName() + ", " + mixerInfo.getVendor() + ", "
	    + mixerInfo.getDescription();

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    Logger.println("Mic:  " + name);
	}

	Line.Info[] infos = mixer.getTargetLineInfo();

	boolean hasTargetDataLine = false;

	for (int i = 0; i < infos.length; i++) {
            if (infos[i] instanceof DataLine.Info) {
		Line line = (Line) mixer.getLine(infos[i]);

		if (line instanceof TargetDataLine) {
		    hasTargetDataLine = true;
		}
	    }
	}

	if (hasTargetDataLine) {
	    if (name.equals(Utils.getPreference(Microphone.MICROPHONE_PREFERENCE))) {
		devices.add(0, name);
            } else {
		devices.add(name);
	    }
	} else {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("No TargetDataLine for " + name);
	    }
	}
    }

    private static void addSpeakerLines(ArrayList<String> devices, 
	Mixer.Info mixerInfo, Mixer mixer) throws LineUnavailableException {

	String name = mixerInfo.getName() + ", " + mixerInfo.getVendor() + ", "
	    + mixerInfo.getDescription();

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    Logger.println("Speaker:  " + name);
	}

	Line.Info[] infos = mixer.getSourceLineInfo();

	boolean hasSourceDataLine = false;

	for (int i = 0; i < infos.length; i++) {
            if (infos[i] instanceof DataLine.Info) {
		Line line = (Line) mixer.getLine(infos[i]);

		if (line instanceof SourceDataLine) {
		    hasSourceDataLine = true;
		}
	    }
	}

	if (hasSourceDataLine) {
	    if (name.equals(Utils.getPreference(Speaker.SPEAKER_PREFERENCE))) {
		devices.add(0, name);
            } else {
		devices.add(name);
	    }
	} else {
	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        Logger.println("No SourceDataLine for " + name);
	    }
	}
    }

    public static boolean equals(String device, Mixer.Info mixerInfo) {
	return device.equals(mixerInfo.getName() + ", " + mixerInfo.getVendor() + ", "
            + mixerInfo.getDescription());
    }

}

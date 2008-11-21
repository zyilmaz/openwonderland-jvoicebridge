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

package com.sun.mpk20.voicelib.impl.service.voice;

import com.sun.mpk20.voicelib.app.BridgeInfo;

import com.sun.voip.CallParticipant;

public class Work {

    public static final int SETUPCALL = 0;
   
    public static final int SETPRIVATEMIX = 1;

    public static final int NEWINPUTTREATMENT = 2;

    public static final int RESTARTINPUTTREATMENT = 3;

    public static final int STOPINPUTTREATMENT = 4;

    public static final int PLAYTREATMENTTOCALL = 5;

    public static final int PAUSETREATMENTTOCALL = 6;

    public static final int STOPTREATMENTTOCALL = 7;

    public static final int MUTECALL = 8;

    public static final int STARTRECORDING = 9;

    public static final int STOPRECORDING = 10;

    public static final int MIGRATECALL = 11;

    public static final int ENDCALL = 12;

    public int command;
    public CallParticipant cp;
    public String fromCallId;
    public String targetCallId;
    public double[] privateMixParameters;
    public String treatment;
    public BridgeInfo bridgeInfo;
    public boolean isMuted;
    public String recordingFile;
    public String phoneNumber;

    public Work(int command) {
	this.command = command;
    }

    public Work(int command, String targetCallId) {
	this.command = command;
	this.targetCallId = targetCallId;
    }

}

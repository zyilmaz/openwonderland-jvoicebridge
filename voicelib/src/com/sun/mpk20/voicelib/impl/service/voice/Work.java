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

import com.sun.voip.CallParticipant;

public class Work {

    public static final int SETUPCALL = 0;
   
    public static final int SETPRIVATEMIX = 1;

    public static final int NEWINPUTTREATMENT = 2;

    public static final int RESTARTINPUTTREATMENT = 3;

    public static final int STOPINPUTTREATMENT = 4;

    public int command;
    public CallParticipant cp;
    public String sourceCallId;
    public String targetCallId;
    public double[] privateMixParameters;
    public String treatment;
    public String bridgeInfo;

    public Work(CallParticipant cp, String bridgeInfo) {
	this.cp = cp;
	this.bridgeInfo = bridgeInfo;
	command = SETUPCALL;
    }

    public Work(String sourceCallId, String targetCallId, 
	    double[] privateMixParameters) {

	this.sourceCallId = sourceCallId;
	this.targetCallId = targetCallId;
	this.privateMixParameters = privateMixParameters;
	command = SETPRIVATEMIX;
    }

    public Work(int command, String callId, String treatment) {
	this.command = command;
	this.targetCallId = callId;
	this.treatment = treatment;
    }

}

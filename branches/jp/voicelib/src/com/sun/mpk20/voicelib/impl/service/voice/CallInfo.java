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

import java.io.Serializable;

import java.util.concurrent.ConcurrentHashMap;

import com.sun.voip.CallParticipant;

/*
 * This most likely has to be kept in a persistent database for recovery!
 */
class CallInfo implements Serializable {

    public CallParticipant cp; 
    public BridgeConnection bridgeConnection;

    public ConcurrentHashMap<String, String> privateMixes =
	new ConcurrentHashMap<String, String>();

    public CallInfo(CallParticipant cp, BridgeConnection bridgeConnection) {
	this.cp = cp;
	this.bridgeConnection = bridgeConnection;
    }

    public String toString() {
	return cp.getCallId() + ":  connected on " + bridgeConnection;
    }

}

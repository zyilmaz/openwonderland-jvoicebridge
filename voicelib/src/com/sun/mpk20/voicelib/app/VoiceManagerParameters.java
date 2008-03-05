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

package com.sun.mpk20.voicelib.app;

/**
 * VoiceManager parameters
 */
public class VoiceManagerParameters {

    public int logLevel;
    public double liveMaxVolume;
    public double liveZeroVolRadius;
    public double liveFullVolRadius;
    public double liveFalloff;
    public double stationaryMaxVolume;
    public double stationaryZeroVolRadius;
    public double stationaryFullVolRadius;
    public double stationaryFalloff;
    public double outworlderMaxVolume;
    public double outworlderZeroVolRadius;
    public double outworlderFullVolRadius;
    public double outworlderFalloff;

    public VoiceManagerParameters() {
    }

    public VoiceManagerParameters(int logLevel, 
	    double liveMaxVolume,
            double liveZeroVolRadius, 
	    double liveFullVolRadius,
	    double liveFalloff,
	    double stationaryMaxVolume,
	    double stationaryZeroVolRadius,
	    double stationaryFullVolRadius,
	    double stationaryFalloff,
	    double outworlderMaxVolume,
	    double outworlderZeroVolRadius,
	    double outworlderFullVolRadius,
	    double outworlderFalloff) {

	this.logLevel = logLevel;

	this.liveMaxVolume = liveMaxVolume;
        this.liveZeroVolRadius = liveZeroVolRadius;
        this.liveFullVolRadius = liveFullVolRadius;
        this.liveFalloff = liveFalloff;

	this.stationaryMaxVolume = stationaryMaxVolume;
        this.stationaryZeroVolRadius = stationaryZeroVolRadius;
        this.stationaryFullVolRadius = stationaryFullVolRadius;
        this.stationaryFalloff = stationaryFalloff;

	this.outworlderMaxVolume = outworlderMaxVolume;
	this.outworlderZeroVolRadius = outworlderZeroVolRadius;
	this.outworlderFullVolRadius = outworlderFullVolRadius;
	this.outworlderFalloff = outworlderFalloff;
    }

}

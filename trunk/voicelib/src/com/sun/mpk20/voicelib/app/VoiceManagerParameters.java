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
    public double defaultMaxVolume;
    public double defaultZeroVolRadius;
    public double defaultFullVolRadius;
    public double defaultFalloff;
    public double orbMaxVolume;
    public double orbZeroVolRadius;
    public double orbFullVolRadius;
    public double orbFalloff;

    public VoiceManagerParameters() {
    }

    public VoiceManagerParameters(int logLevel, 
	    double liveMaxVolume,
            double liveZeroVolRadius, 
	    double liveFullVolRadius,
	    double liveFalloff,
	    double defaultMaxVolume,
	    double defaultZeroVolRadius,
	    double defaultFullVolRadius,
	    double defaultFalloff,
	    double orbMaxVolume,
	    double orbZeroVolRadius,
	    double orbFullVolRadius,
	    double orbFalloff) {

	this.logLevel = logLevel;

	this.liveMaxVolume = liveMaxVolume;
        this.liveZeroVolRadius = liveZeroVolRadius;
        this.liveFullVolRadius = liveFullVolRadius;
        this.liveFalloff = liveFalloff;

	this.defaultMaxVolume = defaultMaxVolume;
        this.defaultZeroVolRadius = defaultZeroVolRadius;
        this.defaultFullVolRadius = defaultFullVolRadius;
        this.defaultFalloff = defaultFalloff;

	this.orbMaxVolume = orbMaxVolume;
	this.orbZeroVolRadius = orbZeroVolRadius;
	this.orbFullVolRadius = orbFullVolRadius;
	this.orbFalloff = orbFalloff;
    }

}

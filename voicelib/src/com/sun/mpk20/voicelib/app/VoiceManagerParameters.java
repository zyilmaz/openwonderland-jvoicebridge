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
    public double liveFalloff;
    public double liveZeroVolRadius;
    public double liveFullVolRadius;
    public double liveMaxVolume;
    public double defaultFalloff;
    public double defaultZeroVolRadius;
    public double defaultFullVolRadius;
    public double defaultMaxVolume;
    public double orbFalloff;
    public double orbZeroVolRadius;
    public double orbFullVolRadius;
    public double orbMaxVolume;

    public VoiceManagerParameters() {
    }

    public VoiceManagerParameters(int logLevel, 
	    double liveFalloff,
            double liveZeroVolRadius, 
	    double liveFullVolRadius,
	    double liveMaxVolume,
	    double defaultFalloff,
	    double defaultZeroVolRadius,
	    double defaultFullVolRadius,
	    double defaultMaxVolume,
	    double orbFalloff,
	    double orbZeroVolRadius,
	    double orbFullVolRadius,
	    double orbMaxVolume) {

	this.logLevel = logLevel;

        this.liveFalloff = liveFalloff;
        this.liveZeroVolRadius = liveZeroVolRadius;
        this.liveFullVolRadius = liveFullVolRadius;
	this.liveMaxVolume = liveMaxVolume;

        this.defaultFalloff = defaultFalloff;
        this.defaultZeroVolRadius = defaultZeroVolRadius;
        this.defaultFullVolRadius = defaultFullVolRadius;
	this.defaultMaxVolume = defaultMaxVolume;

	this.orbFalloff = orbFalloff;
	this.orbZeroVolRadius = orbZeroVolRadius;
	this.orbFullVolRadius = orbFullVolRadius;
	this.orbMaxVolume = orbMaxVolume;
    }

}

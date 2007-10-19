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
    public double defaultFalloff;
    public double liveFullVolRadius;
    public double liveZeroVolRadius;
    public double defaultFullVolRadius;
    public double defaultZeroVolRadius;

    public VoiceManagerParameters() {
    }

    public VoiceManagerParameters(int logLevel, double liveFalloff,
            double defaultFalloff, double liveFullVolRadius,
	    double liveZeroVolRadius,
	    double defaultFullVolRadius,
	    double defaultZeroVolRadius) {

	this.logLevel = logLevel;
        this.liveFalloff = liveFalloff;
        this.defaultFalloff = defaultFalloff;
        this.liveFullVolRadius = liveFullVolRadius;
        this.liveZeroVolRadius = liveZeroVolRadius;
        this.defaultFullVolRadius = defaultFullVolRadius;
        this.defaultZeroVolRadius = defaultZeroVolRadius;
    }

}

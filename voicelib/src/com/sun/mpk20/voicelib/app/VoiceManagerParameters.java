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

    public double livePlayerFalloff;
    public double defaultFalloff;
    public double livePlayerFullVolumeRadius;
    public double livePlayerZeroVolumeRadius;
    public double defaultFullVolumeRadius;
    public double defaultZeroVolumeRadius;

    public VoiceManagerParameters() {
    }

    public VoiceManagerParameters(double livePlayerFalloff,
            double defaultFalloff, double livePlayerFullVolumeRadius,
	    double livePlayerZeroVolumeRadius,
	    double defaultFullVolumeRadius,
	    double defaultZeroVolumeRadius) {

        this.livePlayerFalloff = livePlayerFalloff;
        this.defaultFalloff = defaultFalloff;
        this.livePlayerFullVolumeRadius = livePlayerFullVolumeRadius;
        this.livePlayerZeroVolumeRadius = livePlayerZeroVolumeRadius;
        this.defaultFullVolumeRadius = defaultFullVolumeRadius;
        this.defaultZeroVolumeRadius = defaultZeroVolumeRadius;
    }

}

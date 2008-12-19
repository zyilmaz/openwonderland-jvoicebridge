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

import java.io.Serializable;

public class InverseFalloff extends FalloffFunction 
	implements Serializable {

    public double getVolume(double distance) {
	/*
	 * Calculate how far distance is from 
	 * the zero volume radius.  Values range from 0 to 1.
	 */
	if (attenuationDistance <= 0) {
	    return 1;  // full volume
	}

	double d = distance / attenuationDistance;

	/*
	 * Multiply the distance by 100 and convert to an int
	 * so that a distance of 0 will become 0, .01, 1, etc.
	 *
	 * Now apply a function which is 1 when distance is 0
	 * and zero (or near zero) when distance is 1.  
	 *
	 * f(0) = 1;
	 * f(1) = falloff * f(0);
	 * f(2) = falloff * f(1);
	 * ...
	 * 
	 * In general f(x) = falloff ** x;
	 *
	 * When falloff is < 1, f(x) will decrease as x increases.
	 */
	int iD = (int) (d * 100);

	double v = Math.pow(falloff, iD);

	logger.finest("InverseFalloff d " + Util.round100(distance) 
	   + " fvr " + fullVolumeRadius + " zvr " + zeroVolumeRadius
	   + " a " + Util.round100(attenuationDistance) 
	   + " d/a " + Util.round100(distance / attenuationDistance) 
	   + " falloff " + Util.round100(falloff) + " iD " + iD 
	   + " v " + Util.round100(v));

        return v;
    }

    public Object clone() {
	InverseFalloff i = new InverseFalloff();

        i.falloff = falloff;
        i.maximumVolume = maximumVolume;
        i.fullVolumeRadius = fullVolumeRadius;
        i.zeroVolumeRadius = zeroVolumeRadius;
        i.attenuationDistance = attenuationDistance;

	return i;
    }
	
}

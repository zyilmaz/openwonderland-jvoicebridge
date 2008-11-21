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

import java.util.logging.Logger;

public abstract class FalloffFunction implements Serializable {
    
    protected static final long serialVersionUID = 1;

    /** a logger */
    protected static final Logger logger =
            Logger.getLogger(FalloffFunction.class.getName());

    protected double falloff;
    protected double maximumVolume;
    protected double fullVolumeRadius;
    protected double zeroVolumeRadius;

    protected double attenuationDistance;

    public void setFalloff(double falloff) {
        this.falloff = falloff;
    }

    public double getFalloff() {
	return falloff;
    }

    public void setFullVolumeRadius(double fullVolumeRadius) {
	this.fullVolumeRadius = fullVolumeRadius;

	if (fullVolumeRadius > zeroVolumeRadius) {
            logger.warning("full volume radius " + fullVolumeRadius
	        + " > zero volume radius " + zeroVolumeRadius
                + ", adjusting zvr to fvr");

	    zeroVolumeRadius = fullVolumeRadius;
	}

	attenuationDistance = zeroVolumeRadius - fullVolumeRadius;
    }

    public double getFullVolumeRadius() {
	return fullVolumeRadius;
    }

    public void setZeroVolumeRadius(double zeroVolumeRadius) {
	this.zeroVolumeRadius = zeroVolumeRadius;

        if (zeroVolumeRadius < fullVolumeRadius) {
            logger.warning("zero volume radius " + zeroVolumeRadius
		+ " < full volume radius " + fullVolumeRadius
                + ", adjusting zvr to fvr");

            this.zeroVolumeRadius = fullVolumeRadius;
        }

	attenuationDistance = zeroVolumeRadius - fullVolumeRadius;
    }

    public double getZeroVolumeRadius() {
	return zeroVolumeRadius;
    }

    public void setMaximumVolume(double maximumVolume) {
	this.maximumVolume = maximumVolume;
    }

    public double getMaximumVolume() {
	return maximumVolume;
    }

    /*
     * Distance is a value from 0 to 1 representing a fraction
     * of the window width.
     */
    public double distanceToVolumeLevel(double distance) {
	logger.finest("distance " + Util.round100(distance) 
	    + " fvr " + Util.round100(fullVolumeRadius)
	    + " zvr " + Util.round100(zeroVolumeRadius) + " this " + this);

	if (distance <= fullVolumeRadius) {
	    logger.finest("distance < zvr, full volume d " + Util.round100(distance)
		+ " fvr " + Util.round100(fullVolumeRadius) + " zvr " 
		+ Util.round100(zeroVolumeRadius));

	    return maximumVolume; // * falloff;
	}

	if (distance >= zeroVolumeRadius) {
	    logger.finest("Distance > zvr, v is 0, d " + distance
		+ " fvr " + Util.round100(fullVolumeRadius) + " zvr " 
		+ Util.round100(zeroVolumeRadius));

	    return 0.0;
	}

	/*
	 * Calculate distance from the full volume radius
	 */
	distance -= fullVolumeRadius;

	double v;

	try {
	    v = getVolume(distance) * maximumVolume;
	} catch (Exception e) {
	    logger.finer("Exception:  " + e.getMessage());
	    v = maximumVolume;
	}

	logger.finest("distance " + Util.round100(distance)
	    + " fvr " + Util.round100(fullVolumeRadius) + " zvr " 
	    + Util.round100(zeroVolumeRadius)
	    + " d - f " + Util.round100(distance - fullVolumeRadius)
	    + " v " + Util.round100(v));

	if (v > maximumVolume) {
	    logger.info("v > maximumVolume");
	    v = maximumVolume;
	}

	if (v < 0) {
	    logger.info("v < 0");
	    v = 0;
	}

	return v; // * falloff;
    }

    protected abstract double getVolume(double distance);

    public abstract Object clone();

}

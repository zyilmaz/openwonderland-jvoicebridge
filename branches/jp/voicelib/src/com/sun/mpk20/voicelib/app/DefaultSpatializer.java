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

public class DefaultSpatializer implements Spatializer, Serializable {

    private static final long serialVersionUID = 1;

    private boolean debug;

    /** a logger */
    protected static final Logger logger =
            Logger.getLogger(DefaultSpatializer.class.getName());

    private FalloffFunction falloffFunction;

    private static final String MAXIMUM_VOLUME =
        "com.sun.server.impl.app.VoiceManager.MAXIMUM_VOLUME";

    public static final double DEFAULT_MAXIMUM_VOLUME = .8;

    private static final String FALLOFF =
        "com.sun.server.impl.app.VoiceManager.FALLOFF";

    private static final double DEFAULT_FALLOFF = .92;

    private static final double DEFAULT_FULL_VOLUME_RADIUS = .1;

    private static final double DEFAULT_ZERO_VOLUME_RADIUS = .26;

    private static double[] sinTable = new double[360];

    private double attenuator = 1.0;

    static {
	for (int i = 0; i < 360; i++) {
	    sinTable[i] = Math.sin(Math.toRadians(i));
	}
    }

    public DefaultSpatializer() {
	falloffFunction = new InverseFalloff();

	String s = System.getProperty(MAXIMUM_VOLUME);

	double maximumVolume = DEFAULT_MAXIMUM_VOLUME;

	if (s != null) {
	    try {
	        maximumVolume = Double.parseDouble(s);
	    } catch (NumberFormatException e) {
	        logger.warning("Invalid maximum volume:  " + s);
	    }
	}

	logger.fine("Maximum volume " + maximumVolume);

	s = System.getProperty(FALLOFF);

	double falloff = DEFAULT_FALLOFF;

	if (s != null) {
	    try {
	        falloff = Double.parseDouble(s);
	    } catch (NumberFormatException e) {
	        logger.warning("Invalid maximum volume:  " + s);
	    }
	}
	
	/*
	 * Initialize falloffFunction
	 */
	setFalloff(falloff);
	setMaximumVolume(maximumVolume);
	setZeroVolumeRadius(DEFAULT_ZERO_VOLUME_RADIUS);
        setFullVolumeRadius(DEFAULT_FULL_VOLUME_RADIUS);
    }

    public void setFalloffFunction(FalloffFunction falloffFunction) {
	this.falloffFunction = falloffFunction;
    }

    public FalloffFunction getFalloffFunction() {
	return falloffFunction;
    }

    public void setFalloff(double falloff) {
        falloffFunction.setFalloff(falloff);

        logger.fine("Set falloff to " + Util.round100(falloff));

        falloffFunction.setFalloff(falloff);
    }

    public double getFalloff() {
	return falloffFunction.getFalloff();
    }

    public void setFullVolumeRadius(double fullVolumeRadius) {
	logger.fine("Set full volume radius to " 
	    + Util.round100(fullVolumeRadius));
	falloffFunction.setFullVolumeRadius(fullVolumeRadius);
    }

    public double getFullVolumeRadius() {
	return falloffFunction.getFullVolumeRadius();
    }

    public void setZeroVolumeRadius(double zeroVolumeRadius) {
	logger.fine("Set zero volume radius to " 
	    + Util.round100(zeroVolumeRadius));
	falloffFunction.setZeroVolumeRadius(zeroVolumeRadius);
    }

    public double getZeroVolumeRadius() {
	return falloffFunction.getZeroVolumeRadius();
    }

    public void setMaximumVolume(double maximumVolume) {
	logger.fine("Set maximum volume to " + Util.round100(maximumVolume));
	falloffFunction.setMaximumVolume(maximumVolume);
    }

    public double getMaximumVolume() {
	return falloffFunction.getMaximumVolume();
    }

    /*
     * If the volume is 1, then set zeroVolumeRadius and fullVolumeRadius
     * to the default values.
     *
     * If the volume is greater than 1, increase the fullVolumeRadius thereby
     * making things closer.  Set zeroVolumeRadius to the default.
     *
     * If the volume is < 1, decrease the zero volume radius theremy making
     * things farther away.  Set fullVolumeRadius to the default.
     */
    public void adjustVolumeRadii(double volume) {
	if (volume == 1) {
	    setFullVolumeRadius(DEFAULT_FULL_VOLUME_RADIUS);
	    setZeroVolumeRadius(DEFAULT_ZERO_VOLUME_RADIUS);
	    System.out.println("adjusting volume to defaults");
	} else if (volume > 1) {
	    setFullVolumeRadius(DEFAULT_FULL_VOLUME_RADIUS + volume - 1);
	    setZeroVolumeRadius(DEFAULT_ZERO_VOLUME_RADIUS);
	    System.out.println("volume " + volume + " adjusting full vol radius to "
		+ (DEFAULT_FULL_VOLUME_RADIUS + volume - 1));
	} else {
	    setFullVolumeRadius(DEFAULT_FULL_VOLUME_RADIUS);
	    setZeroVolumeRadius(DEFAULT_ZERO_VOLUME_RADIUS - volume);
	    System.out.println("volume " + volume + " adjusting zero vol radius to "
		+ (DEFAULT_ZERO_VOLUME_RADIUS - volume));
	}
    }

    public void setAttenuator(double attenuator) {
	this.attenuator = attenuator;
    }

    public double getAttenuator() {
	return attenuator;
    }

    public double[] spatialize(double sourceX, double sourceY, 
	    double sourceZ, double sourceOrientation,
	    double destX, double destY, double destZ, 
	    double destOrientation) {

	Position p2 = new Position(sourceX, sourceY, sourceZ,
	    sourceOrientation);

	Position p1 = new Position(destX, destY, destZ, destOrientation);

	/*
	 * Here are the assumptions about what happens to the sound volume
	 * when someone speaks:
	 *
 	 * - the volume is decreases with distance.
	 * - The angle of the listener determines how much volume goes to
	 *   each ear of the listener.  If the angle of the listener is 0
	 *   relative to the speaker, the volume goes to both ears equally.
	 * 
	 * The problem can be simplified by noticing that if the distance
	 * between p1 and p2 is non-zero, then there is always
	 * a straight line between p1 and p2 in 3-space.
	 * This straight line can be rotated so that it is parallel 
	 * to the x-axis by subtracting out the angle between the two points.
	 * In doing so, the angles of each point must also have the angle
	 * between the points subtracted out.
	 * 
	 * Now it must be determined which point is in front of the other.
	 * If the angle at which sounds hits p1 is between 90 and 270 degrees,
	 * p2 is behind p1.  Otherwise p2 is in front of p1.
	 */

	/*
	 * This is the distance between the two calls in 3-space.
	 * It is a value between 0 and 1 which represents a fraction
	 * of the window size.
	 *
	 * It is used to attenuate the volume.
	 */
	double distance = Util.getDistance(p1, p2);

	System.out.println("distance " + distance + " p1 " + p1 + " p2 " + p2);

	double volume = falloffFunction.distanceToVolumeLevel(distance);

        /*
         * p1ReceiveAngle is the angle at call 1 at which the sound from call 2 
	 * hits call 1.
         */
	debug = false;

	if (volume > .1) {
	    //debug = true;
	}

	double p1ReceiveAngle = Util.getAngle(p1, p2);

	double p1ReceiveDegrees = Util.toDegrees(p1ReceiveAngle);

	logger.finest(p1 + " to " + p2 + " receiveDegrees " + p1ReceiveDegrees);

	if (debug) {
	    logger.info("receive angle " + p1ReceiveDegrees);
	}

	/*
	 * parameter 0 is front/back with 1 being front and -1 back
	 * parameter 1 is left/right with -1 being left and 1 right
	 * parameter 2 is up/down with -1 being down and 1 up
	 * parameter 3 is the volume 0 to maxVolume (usually 1)
	 */
	double[] privateMixParameters = new double[4];

	privateMixParameters[2] = 0;	// up/down

	if (p1ReceiveDegrees <= 90) {
            privateMixParameters[0] = 1 - (p1ReceiveDegrees / 90);
	    privateMixParameters[1] = -p1ReceiveDegrees / 90;
	} else if (p1ReceiveDegrees <= 180) {
	    double d = p1ReceiveDegrees - 90;
            privateMixParameters[0] = -d / 90;
	    privateMixParameters[1] = -(1 - (d / 90));

	    logger.finest("d " + d + " p0 " + Util.round100(privateMixParameters[0]));
	} else if (p1ReceiveDegrees <= 270) {
	    double d = p1ReceiveDegrees - 180;
            privateMixParameters[0] = -(1 - (d / 90));
	    privateMixParameters[1] = d / 90;
	} else {
	    double d = p1ReceiveDegrees - 270;
            privateMixParameters[0] = d / 90;
	    privateMixParameters[1] = 1 - (d / 90);
	}

	// Try this for smoother transitions.
        privateMixParameters[0] = Math.cos(p1ReceiveAngle);
	privateMixParameters[1] = -Math.sin(p1ReceiveAngle);
	//privateMixParameters[1] = -sinTable[(int) p1ReceiveDegrees];

        privateMixParameters[3] = volume * attenuator;    // volume

	privateMixParameters[0] = Util.round100(privateMixParameters[0]);
	privateMixParameters[1] = Util.round100(privateMixParameters[1]);
	privateMixParameters[2] = Util.round100(privateMixParameters[2]);
	privateMixParameters[3] = Util.round100(privateMixParameters[3]);

	logger.finest("p1Rec " + p1ReceiveDegrees
	    + " privateMixParameters " + privateMixParameters[0] + ", " 
	    + privateMixParameters[1] + ", " + privateMixParameters[2] + ", "
	    +  privateMixParameters[3]);

	return privateMixParameters;
    }
    public static void main(String[] args) {
	DefaultSpatializer spatializer = new DefaultSpatializer();

	int n = 40000;

	doit(spatializer, n);

	long start = System.currentTimeMillis();

	doit(spatializer, n);

	long elapsed = System.currentTimeMillis() - start;

	System.out.println("elapsed " + elapsed + " avg " 
	    + ((double) elapsed / n));
    }
 
    private static void doit(DefaultSpatializer spatializer, int n) {
	for (int i = 0; i < n; i++) {
	    double sourceX = i;
	    double sourceZ = i + 1;
	    double sourceOrientation = i;

	    double destX = i + 2;
	    double destZ = i + 3;
	    double destOrientation = i + 1;

    	    spatializer.spatialize(sourceX, 0, 
	        sourceZ, sourceOrientation, destX, 0, 
		destZ, destOrientation);
	}
    }

    public Object clone() {
	DefaultSpatializer d = new DefaultSpatializer();

	d.setFalloffFunction((FalloffFunction) falloffFunction.clone());
	d.setAttenuator(attenuator);

	return d;
    }

    public String toString() {
	return "DefaultSpatializer:  "
	    + " max volume " + getMaximumVolume()
	    + " attenuator " + attenuator
	    + " fvr " + getFullVolumeRadius() 
	    + " zvr " + getZeroVolumeRadius();
    }

}

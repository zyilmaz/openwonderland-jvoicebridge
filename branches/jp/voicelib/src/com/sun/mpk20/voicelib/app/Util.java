package com.sun.mpk20.voicelib.app;

import java.io.Serializable;

import java.util.logging.Logger;

public class Util implements Serializable {

    private static final Logger logger =
            Logger.getLogger(Util.class.getName());


    public static final double PiOver2 = Math.PI / 2;

    private static boolean debug = false;

    /**
     * Generate a unique id containing the specified id
     */
    public static String generateUniqueId(String id) {
        return id;
    }

    public static double getDistance(double sourceX, double sourceY,
                                     double sourceZ, double destX, 
				     double destY, double destZ) {

	double xd = destX - sourceX;
        double zd = destZ - sourceZ;

        double d = Math.sqrt(xd * xd + zd * zd);

        double yd = destY - sourceY;

        double distance = d;

        if (yd != 0) {
            distance = Math.sqrt(d * d + yd * yd);
        }

        return distance;
    }

    public static double getDistance(Position p1, Position p2) {
	double xd = p2.x - p1.x;
	double zd = p2.z - p1.z;

	double d = Math.sqrt(xd * xd + zd * zd);

	double yd = p2.y - p1.y;

	double distance = d;

	if (yd != 0) {
	    distance = Math.sqrt(d * d + yd * yd);
	}

	logger.finest(
	    "P1 (" + round100(p1.x) + "," + round100(p1.y) + ")"
	    + " P2 (" + round100(p2.x) + "," + round100(p2.y) + ")"
	    + " distance " + round100(distance));

	return distance;
    }

    /*
     * Get angle at which sound from p2 hits p1
     */
    public static double getAngle(Position p1, Position p2) {
	if (p1.x == p2.x && p1.z == p2.z) {
	    /*
	     * p1 and p2 are at the same place
	     * Their rotations don't matter.
	     * Treat them like they are facing each other.
	     */
	    return 0;
	}

	double p1ReceiveAngle;

	if (p1.x == p2.x) {
	    /*
	     * p1 and p2 are along the same vertical line.
	     */
            if (p1.z < p2.z) {
		/*
		 * p1 is below p2, the angle is correct as is.
		 */
                p1ReceiveAngle = PiOver2;         // 90 degrees
            } else {
		/*
		 * p1 is above p2, p1 is receiving audio 
		 * on the opposite size.
		 */
                p1ReceiveAngle = -PiOver2;        // -90 degrees
	    }
	} else {
	    p1ReceiveAngle = Math.atan((p2.z - p1.z) / (p2.x - p1.x));
	}

	if (p1.x > p2.x) {
	    logger.finest("p1RecAngle needs PI added " + toDegrees(p1ReceiveAngle));
	    p1ReceiveAngle += Math.PI;
	} 

	logger.finest("p1=" + p1 + " p2=" + p2
            + " p1RecAng " + toDegrees(p1ReceiveAngle) + " p1 orient " 
	    + toDegrees(p1.orientation)
	    + " p1RecAng adj " + toDegrees(p1ReceiveAngle - p1.orientation));

	p1ReceiveAngle -= p1.orientation;

	return p1ReceiveAngle;
    }

    public static int toDegrees(double radians) {
        int degrees = ((int) Math.toDegrees(radians)) % 360;

        if (degrees < 0) {
	    logger.finest("degrees < 0, adding 360 " + degrees);
            degrees += 360;
        }

        return degrees;
    }

    public static double round100(double v) {
        return Math.round(v * 100) / (double) 100;
    }

    public static double round1000(double v) {
        return Math.round(v * 1000) / (double) 1000;
    }

}

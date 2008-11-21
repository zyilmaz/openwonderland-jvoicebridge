package com.sun.mpk20.voicelib.app;

import com.sun.sgs.app.AppContext;

import java.io.Serializable;

public class AmbientSpatializer implements Spatializer, Serializable {
    double minX;
    double maxX;
    double minY;
    double maxY;
    double minZ;
    double maxZ;

    double attenuator = 1.0;

    private static double scale;

    static {
        scale =  AppContext.getManager(VoiceManager.class).getScale();
    }

    public AmbientSpatializer() {
    }
	
    public AmbientSpatializer(double lowerLeftX, double lowerLeftY,
 	double lowerLeftZ, double upperRightX,
	double upperRightY, double upperRightZ) {

	setBounds(lowerLeftX, lowerLeftY, lowerLeftZ,
	    upperRightX, upperRightY, upperRightZ);
    }
        
    public void setBounds(double lowerLeftX, double lowerLeftY,
	double lowerLeftZ, double upperRightX,
	double upperRightY, double upperRightZ) {

	//logger.finest("lX " + lowerLeftX + " lY " + lowerLeftY
	//	+ " lZ " + lowerLeftZ + " uX " + upperRightX
	//	+ " uY " + upperRightY + " uZ " + upperRightZ);

        minX = Math.min(lowerLeftX / scale, upperRightX / scale);
        maxX = Math.max(lowerLeftX / scale, upperRightX / scale);
        minY = Math.min(lowerLeftY / scale, upperRightY / scale);
        maxY = Math.max(lowerLeftY / scale, upperRightY / scale);
        minZ = Math.min(lowerLeftZ / scale, upperRightZ / scale);
        maxZ = Math.max(lowerLeftZ / scale, upperRightZ / scale);
    }

    public double[] spatialize(double sourceX, double sourceY, 
                               double sourceZ, double sourceOrientation, 
                               double destX, double destY, 
                               double destZ, double destOrientation)
    {
	// see if the destination is inside the ambient audio range
        if (isInside(destX, destY, destZ)) {
	    //logger.finest("inside min (" + round(minX) + ", " + round(minY) 
	    //  + ", " + round(minZ) + ") "
	    //  + " max (" + round(maxX) + ", " + round(maxY) + ", " 
	    //  + round(maxZ) + ") " + " dest (" + round(destX) + ", " 
	    //  + round(destY) + ", " + round(destZ) + ")");
	    return new double[] { 0, 0, 0, attenuator };
        } else {
	    //logger.info("outside min (" + round(minX) + ", " + round(minY) 
	    //  + ", " + round(minZ) + ") "
	    //  + " max (" + round(maxX) + ", " + round(maxY) + ", " 
	    //  + round(maxZ) + ") " + " dest (" + round(destX) + ", " 
	    //  + round(destY) + ", " + round(destZ) + ")");
            return new double[] { 0, 0, 0, 0 };
        }
    }
        
    private boolean isInside(double x, double y, double z) {
	//logger.info("isInside: x " + round(x) + " y " + round(y) 
	//	+ " z " + z
	//	+ " minX " + round(minX) + " maxX " + round(maxX)
	//	+ " minY " + round(minY) + " maxY " + round(maxY)
	//	+ " minZ " + minZ + " maxZ " + maxZ); 

	/*
	 * Don't check z because we always expect 0, but it's always
	 * non-zero.
	 */
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    public void setAttenuator(double attenuator) {
	this.attenuator = attenuator;
    }

    public double getAttenuator() {
	return attenuator;
    }

    public Object clone() {
        AmbientSpatializer a = new AmbientSpatializer();

	a.minX = minX;
        a.maxX = maxX;
        a.minY = minY;
        a.maxY = maxY;
        a.minZ = minZ;
        a.maxZ = maxZ;
	a.attenuator = attenuator;

	return a;
    }

    public String toString() {
	return "AmbientSpatializer:  minX=" + minX + " maxX=" + maxX
	    + " minY=" + minY + " minZ=" + minZ + " maxZ=" + maxZ 
	    + " attenuator=" + attenuator;
    }

}

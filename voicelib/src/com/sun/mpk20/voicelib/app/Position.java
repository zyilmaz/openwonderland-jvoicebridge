package com.sun.mpk20.voicelib.app;

import java.io.Serializable;

public class Position implements Serializable {

    public double x;
    public double y;
    public double z;
    public double orientation;
    
    public Position(double x, double y, double z, double orientation) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.orientation = orientation;
    }

    public String toString() {
        return "(" + Util.round100(x) + ", " + Util.round100(y) + ", " 
    	    + Util.round100(z) + ")" + ":" + Util.toDegrees(orientation);
    }

}

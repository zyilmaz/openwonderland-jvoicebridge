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

package com.sun.mpk20.voicelib.impl.app;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.mpk20.voicelib.app.Spatializer;

public class Player {

    public String callId;

    public double x;
    public double y;
    public double z;
    public double orientation;  

    public double attenuationRadius;
    public double attenuationVolume = 1.0;

    public boolean isLivePerson;

    public Spatializer spatializer;

    private ConcurrentHashMap<String, Spatializer> privateSpatializers =
	new ConcurrentHashMap<String, Spatializer>();

    private ArrayList<Player> playersInRange = new ArrayList<Player>();

    public Player(String callId, double x, double y, double z,
	    double orientation) {

	this.callId = callId;
	setPosition(x, y, z);
	setOrientation(orientation);
    }

    public void setPosition(double x, double y, double z) {
    	this.x = x;
	this.y = y;
	this.z = z;
    }

    public void setOrientation(double orientation) {
	this.orientation = Math.toRadians(orientation);
    }

    public void setAttenuationRadius(double attenuationRadius) {
        this.attenuationRadius = attenuationRadius;
    }

    public void setAttenuationVolume(double attenuationVolume) {
        this.attenuationVolume = attenuationVolume;
    }

    public void setSpatializer(Spatializer spatializer) {
	this.spatializer = spatializer;
    }

    public Spatializer getSpatializer() {
	return spatializer;
    }

    public void setLivePerson() {
	isLivePerson = true;
    }

    public void setPrivateSpatializer(String callId, Spatializer spatializer) {
	if (spatializer == null) {
	    privateSpatializers.remove(callId);
	    return;
	}

	privateSpatializers.put(callId, spatializer);
    }

    public Spatializer getPrivateSpatializer(String callId) {
	return privateSpatializers.get(callId);
    }

    public boolean isInRange(Player p) {
	synchronized (playersInRange) {
	    return playersInRange.contains(p);
	}
    }

    public void addPlayerInRange(Player p) {
	synchronized (playersInRange) {
	    playersInRange.add(p);
	}
    }

    public void removePlayerInRange(Player p) {
	synchronized (playersInRange) {
	    playersInRange.remove(p);
	}
    }

    public String toString() {
	double xR = Math.round(x * 100) / (double) 100;
	double yR = Math.round(y * 100) / (double) 100;
	double zR = Math.round(z * 100) / (double) 100;

	int a = ((int) Math.toDegrees(orientation)) % 360;

	if (a < 0) {
	    a += 360;
	}

	return callId + ":(" + xR + "," + yR + "," + zR + "," + a + ")";
    }

}

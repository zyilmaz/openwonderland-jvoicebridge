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
import java.util.Collection;
import java.util.Iterator;
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

    private boolean isLivePerson;

    private Spatializer publicSpatializer;

    private Spatializer incomingSpatializer;

    private double talkAttenuator = 1.0;
    private double listenAttenuator = 1.0;

    public boolean positionChanged;
    public boolean orientationChanged;

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
    	x = Math.round(x * 100) / 100.;
	y = Math.round(y * 100) / 100.;
	z = Math.round(z * 100) / 100.;

	positionChanged = this.x != x || this.y != y | this.z != z;

	this.x = x;
	this.y = y;
	this.z = z;
    }

    public boolean samePosition(double x, double y, double z) {
    	x = Math.round(x * 100) / 100.;
	y = Math.round(y * 100) / 100.;
	z = Math.round(z * 100) / 100.;

	return this.x == x && this.y == y && this.z == z;
    }
	
    public void setOrientation(double orientation) {
	orientation = Math.round(Math.toRadians(orientation) * 100) / 100.;

	orientationChanged = this.orientation != orientation;

	this.orientation = orientation;
    }

    public boolean sameOrientation(double orientation) {
	orientation = Math.round(Math.toRadians(orientation) * 100) / 100.;

	return this.orientation == orientation;
    }

    public void setAttenuationRadius(double attenuationRadius) {
        this.attenuationRadius = attenuationRadius;
    }

    public void setAttenuationVolume(double attenuationVolume) {
        this.attenuationVolume = attenuationVolume;
    }

    public void setPublicSpatializer(Spatializer publicSpatializer) {
	this.publicSpatializer = publicSpatializer;
    }

    public Spatializer getPublicSpatializer() {
	return publicSpatializer;
    }

    public void setIncomingSpatializer(Spatializer incomingSpatializer) {
	this.incomingSpatializer = incomingSpatializer;
    }

    public Spatializer getIncomingSpatializer() {
	return incomingSpatializer;
    }

    public void setLivePerson() {
	isLivePerson = true;
    }

    public boolean isLivePerson() {
	return isLivePerson;
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

    public void setTalkAttenuator(double talkAttenuator) {
	if (talkAttenuator < 0) {
	    talkAttenuator = 0;
	}
	
	this.talkAttenuator = talkAttenuator;
    }

    public double getTalkAttenuator() {
	return talkAttenuator;
    }

    private double lastListenAttenuator = 1.0;

    public void setListenAttenuator(double listenAttenuator) {
	this.listenAttenuator = listenAttenuator;

if (false) {
        /*
         * If we have any private spatializers, adjust their attenuation.
         */
	Collection<Spatializer> spatializers = privateSpatializers.values();

	Iterator<Spatializer> iterator = spatializers.iterator();
	
	while (iterator.hasNext()) {
	    Spatializer spatializer = iterator.next();
	
	    spatializer.setAttenuator(
		spatializer.getAttenuator() / lastListenAttenuator *
		listenAttenuator);
	}

	lastListenAttenuator = listenAttenuator;
}
    }

    public double getListenAttenuator() {
	return listenAttenuator;
    }

    public boolean isInRange(Player p) {
	synchronized (playersInRange) {
	    return playersInRange.contains(p);
	}
    }

    public void addPlayerInRange(Player p) {
	synchronized (playersInRange) {
	    if (playersInRange.contains(p)) {
		System.out.println("playersInRange already contains " + p);
	    }
	    playersInRange.add(p);
	}
    }

    public void removePlayerInRange(Player p) {
	synchronized (playersInRange) {
	    if (playersInRange.contains(p) == false) {
		System.out.println("playersInRange doesn't contain " + p);
	    }
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

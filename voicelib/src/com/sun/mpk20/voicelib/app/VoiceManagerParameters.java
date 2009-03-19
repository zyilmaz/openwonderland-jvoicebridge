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

/**
 * VoiceManager parameters
 */
public class VoiceManagerParameters implements Serializable {

    public int logLevel;
    public double scale;
    public String conferenceId;
    public DefaultSpatializer livePlayerSpatializer;
    public DefaultSpatializer stationarySpatializer;
    public DefaultSpatializer outworlderSpatializer;
    public AudioGroup livePlayerAudioGroup;
    public AudioGroup stationaryPlayerAudioGroup;

    public VoiceManagerParameters() {
    }

    public VoiceManagerParameters(int logLevel, double scale, String conferenceId,
	     DefaultSpatializer livePlayerSpatializer,
	     DefaultSpatializer stationarySpatializer,
	     DefaultSpatializer outworlderSpatializer,
	     AudioGroup livePlayerAudioGroup,
	     AudioGroup stationaryPlayerAudioGroup) {

	this.logLevel = logLevel;
	this.scale = scale;
	this.conferenceId = conferenceId;
	this.livePlayerSpatializer = livePlayerSpatializer;
	this.stationarySpatializer = stationarySpatializer;
	this.outworlderSpatializer = outworlderSpatializer;
	this.livePlayerAudioGroup = livePlayerAudioGroup;
	this.stationaryPlayerAudioGroup = stationaryPlayerAudioGroup;
    }

}

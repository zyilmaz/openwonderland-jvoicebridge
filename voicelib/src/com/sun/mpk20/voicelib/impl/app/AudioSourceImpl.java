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

import com.sun.mpk20.voicelib.app.AudioSink;
import com.sun.mpk20.voicelib.app.AudioSource;
import com.sun.mpk20.voicelib.app.BridgeInfo;
import com.sun.mpk20.voicelib.app.Player;

import java.io.Serializable;

public class AudioSourceImpl implements AudioSource, Serializable {

   private Player player;
   private BridgeInfo bridgeInfo;

   public AudioSourceImpl(Player player, BridgeInfo bridgeInfo) {
	this.player = player;
	this.bridgeInfo = bridgeInfo;
   }

   public void setPlayer(Player player) {
	this.player = player;
   }

   // get the Player this Source is attached to
   public Player getPlayer() {
	return player;
   }

   public void setBridgeInfo(BridgeInfo bridgeInfo) {
	this.bridgeInfo = bridgeInfo;
   }

   // get the ID of this Source in the bridge
   public BridgeInfo getBridgeInfo() {
	return bridgeInfo;
   }

   // notification that the given audio source
   // in range of this sourcehas moved.  Return
   // the message to send to the bridge to
   // update the mix for this source
   public String sinkMoved(AudioSink sink,
         double x, double y, double z, double orientation, double attenuation) {

	return null;
   }

} 

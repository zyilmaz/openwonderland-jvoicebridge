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

public interface AudioSink extends Serializable {

   public void setPlayer(Player player);

   // get the Player this sink is attached to
   public Player getPlayer();

   public void setBridgeInfo(BridgeInfo bridgeInfo);

   // get the ID of this sink in the bridge
   public BridgeInfo getBridgeInfo();

   // notification that the given audio source
   // in range of this sink has moved.  Return
   // the message to send to the bridge to
   // update the mix for this sink
   public String sourceMoved(AudioSource source,
         double x, double y, double z, double orientation, double attenuation);

} 

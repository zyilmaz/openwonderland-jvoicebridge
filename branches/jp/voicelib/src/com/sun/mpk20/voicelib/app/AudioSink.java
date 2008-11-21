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

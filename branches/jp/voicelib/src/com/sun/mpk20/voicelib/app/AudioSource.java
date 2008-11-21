package com.sun.mpk20.voicelib.app;

import java.io.Serializable;

public interface AudioSource extends Serializable {

   public void setPlayer(Player player);

   // get the Player this sink is attached to
   public Player getPlayer();

   public void setBridgeInfo(BridgeInfo bridgeInfo);

   // get the ID of this source in the bridge
   public BridgeInfo getBridgeInfo();

   // notification that the given audio sink
   // in range of this sink has moved.  Return
   // the message to send to the bridge to
   // update the mix for this source
   public String sinkMoved(AudioSink sink,
         double x, double y, double z, double orientation, double attenuation);

}

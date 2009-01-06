package com.sun.mpk20.voicelib.impl.app;

import com.sun.mpk20.voicelib.app.AudioSink;
import com.sun.mpk20.voicelib.app.AudioSource;
import com.sun.mpk20.voicelib.app.BridgeInfo;
import com.sun.mpk20.voicelib.app.Player;

import java.io.Serializable;

public class AudioSinkImpl implements AudioSink, Serializable {

   private Player player;
   private BridgeInfo bridgeInfo;

   public AudioSinkImpl(Player player, BridgeInfo bridgeInfo) {
	this.player = player;
	this.bridgeInfo = bridgeInfo;
   }

   public void setPlayer(Player player) {
	this.player = player;
   }

   // get the Player this sink is attached to
   public Player getPlayer() {
	return player;
   }

   public void setBridgeInfo(BridgeInfo bridgeInfo) {
	this.bridgeInfo = bridgeInfo;
   }

   // get the ID of this sink in the bridge
   public BridgeInfo getBridgeInfo() {
	return bridgeInfo;
   }

   // notification that the given audio source
   // in range of this sink has moved.  Return
   // the message to send to the bridge to
   // update the mix for this sink
   public String sourceMoved(AudioSource source,
         double x, double y, double z, double orientation, double attenuation) {

	return null;
   }

} 
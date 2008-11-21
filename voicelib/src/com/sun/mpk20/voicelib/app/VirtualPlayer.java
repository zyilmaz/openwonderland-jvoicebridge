package com.sun.mpk20.voicelib.app;

import java.util.ArrayList;

public class VirtualPlayer {

    public Player player;
    public String cellName;
    public Player realPlayer;

    public VirtualPlayer(Player player, String cellName, Player realPlayer) {
	this.player = player;
	this.cellName = cellName;
	this.realPlayer = realPlayer;
    }
	
    public String toString() {
	return player.getId();
    }

}

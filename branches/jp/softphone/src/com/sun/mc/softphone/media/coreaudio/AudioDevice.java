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

package com.sun.mc.softphone.media.coreaudio;

public class AudioDevice extends Object { 
    public String   name;
    public int      id;
    public int      bufferSize;
    public float    sampleRate;
    public int      bytesPerPacket;
    public int      framesPerPacket;
    public int      bytesPerFrame;
    public int      channelsPerFrame;
    public int      bitsPerChannel;
    
    public String toString() {
        return conciseDescription();
    }
    
    public String conciseDescription() {
        String ret = "";
        ret += name + " -- ID:" + id;
        return ret;
    }
    
    public String verboseDescription() {
        String ret = "\n";
        ret += "Name:               " + name + "\n" +
               "ID:                 " + id + "\n" +
               "Buffer Size:        " + bufferSize + "\n" +
               "SampleRate:         " + sampleRate + "\n" +
               "Bytes Per Packet:   " + bytesPerPacket + "\n" +
               "Frames Per Packet:  " + framesPerPacket + "\n" +
               "Bytes Per Frame:    " + bytesPerFrame + "\n" +
               "Channels Per Frame: " + channelsPerFrame + "\n" +
               "Bits Per Channel:   " + bitsPerChannel + "\n";
        return ret;
    }
}

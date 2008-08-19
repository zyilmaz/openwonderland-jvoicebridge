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

package com.sun.mc.softphone.media;

import java.io.IOException;

public interface Speaker {

    public static final String SPEAKER_PREFERENCE =
            "com.sun.mc.softphone.media.SPEAKER";

    /** the speaker buffer size in milliseconds */
    public static final int DEFAULT_BUFFER_SIZE = 160;

    /** the property to read for the buffer size */
    public static final String BUFFER_SIZE_PROPERTY =
        "com.sun.mc.softphone.media.SPEAKER_BUFFER_SIZE";

    /** the property for volume level */
    public static final String VOLUME_LEVEL =
        "com.sun.mc.softphone.media.speaker.VOLUME_LEVEL";

    public void done();

    public int getSampleSizeInBits();

    public int getSampleRate();

    public int getChannels();

    public int getBufferSize();

    public int getBufferSizeMillis();

    public int getBufferSize(int millis);

    public int getBufferSizeMillis(int bufferSize);

    public int available();

    public int write(byte[] buffer, int offset, int length)
	throws IOException;

    public void drain();

    public void flush();

    public void start();

    public void stop();

    public boolean isRunning();

    public void setVolumeLevel(double volumeLevel);

    public double getVolumeLevel();

    public void printStatistics();

}

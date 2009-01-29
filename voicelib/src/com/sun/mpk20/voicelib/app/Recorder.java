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

import java.io.IOException;

public interface Recorder extends Serializable {

    public String getId();

    public RecorderSetup getSetup();

    public void startRecording(String recordingFile) throws IOException;

    public void pauseRecording() throws IOException;

    public void stopRecording() throws IOException;

    public void playRecording(String recordingFile) throws IOException;

    public void pausePlayingRecording() throws IOException;

    public void stopPlayingRecording() throws IOException;

}

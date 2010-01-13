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

package com.sun.voip;

/**
 * notification of recording events
 */
public interface RecorderListener {

    /*
     * Notification that recording has started
     */
    public void recorderStarted();

    /*
     * Notification that recording has stopped and the recording file is closed.
     * The CallStatus RECORDERSTOPPED is sent.
     */
    public void recorderStopped();

    /*
     * Notification that recorderDone() has been called.
     * The CallStatus RECORDERDONE is sent.
     */
    public void recorderDone();

    /*
     * Recorder audio data
     */
    public void recorderData(byte[] buffer, int offset, int length);

}

/*
 * Copyright (c) 1999 - 2001 by Matthias Pfisterer
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*
 *	ClipPlayer.java
 *
 *	This file is part of jsresources.org
 *
 * Copyright 2007 Sun Microsystems, Inc.
 */

package com.sun.mc.softphone.gui;
/*
|<---            this code is formatted to fit into 80 columns             --->|
*/

import com.sun.mc.softphone.media.MediaManager;
import com.sun.mc.softphone.media.MediaManagerFactory;
import java.io.IOException;

public class ClipPlayer {
    private MediaManager manager;

    /*
     *	The clip will be played repeatCount + 1 times.
     */
    public ClipPlayer(String resource, int repeatCount) throws IOException {
	//System.out.println("playing clip " + resource);

	try {
            manager = MediaManagerFactory.getInstance();

	    synchronized(this) {
                manager.startPlayingFile(resource, repeatCount);
	    }
        } catch (Exception e) {
	    throw new IOException(e.getMessage());
	}
    }

    public void stop() {
	synchronized(this) {
	    manager.stopPlayingFile();
	}
    }

}

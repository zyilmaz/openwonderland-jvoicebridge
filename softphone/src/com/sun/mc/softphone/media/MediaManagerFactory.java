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

public class MediaManagerFactory {
    private static MediaManager mediaManager;

    private MediaManagerFactory() {
    }

    /**
     * Get an instance of the media manager
     * @return the MediaManager
     */
    public static MediaManager getInstance() {
        return getInstance(true);
    }
    
    /**
     * Get an instance of the media manager
     * @param reuse if true, reuse the existing manager where possible
     * @return a MediaManager, newly allocated if reuse is
     * false
     */
    public static MediaManager getInstance(boolean reuse) {
	if (reuse && mediaManager != null) {
	    return mediaManager;
	}
	
        /*
	 * Find the media manager property
	 */ 
        String name = System.getProperty("com.sun.mc.softphone.MEDIA_MANAGER");
        if (name == null) {
            // the default
            name = "com.sun.mc.softphone.media.MediaManagerImpl";
        }

        try {
            Class clazz = Class.forName(name);
            mediaManager = (MediaManager) clazz.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
        
	return mediaManager;
    }
}

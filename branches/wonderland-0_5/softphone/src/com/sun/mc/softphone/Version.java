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

package com.sun.mc.softphone;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.sun.mc.softphone.common.Utils;

public class Version {

    private static final String VERSION = Utils.getResourcePath("version.txt");

    private Version() {
    }

    public static String getVersion() {
	InputStream in = Version.class.getResourceAsStream(VERSION);

        if (in == null) {
            return "Can't read " + VERSION;
        }

        in = new BufferedInputStream(in);

	int bytesAvailable = 0;

        try {
            bytesAvailable = in.available();
        } catch (IOException e) {
            return e.getMessage();
        }

	byte[] buf = new byte[bytesAvailable];

	try {
	    in.read(buf, 0, bytesAvailable);
	} catch (IOException e) {
            return e.getMessage();
        }

	return new String(buf).trim();
    }

}

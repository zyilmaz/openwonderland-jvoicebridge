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

public interface Spatializer {
    /**
     * Determine the private mix parameters for audio from the given source
     * going to the given destination.
     * @param source the position of the audio source
     * @param dest the position of the audio destination
     * @return an array of 3 doubles:
     *  <ul<li>[0] - the front/back value of the private mix, 
     * a double from 1 (all the way front) to -1 (all the way back)
     *  <li>[1] - the left/right value of the private mix, 
     * a double from 1 (all the way left) to -1 (all the way right)
     *  <li>[2] - the up/down value of the private mix, 
     * a double from 1 (all the way up) to -1 (all the way down)
     *  <li>[3] - the volume of the private mix, from 0 to 10
     */
    public double[] spatialize(double sourceX, double sourceY,
	double sourceZ, double sourceOrientation, double destX, 
	double destY, double destZ, double destOrientation);

} 

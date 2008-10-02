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

package com.sun.mc.softphone.media.alsa;

import junit.framework.*;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;

public class AudioDriverAlsaTest extends TestCase {
    
    private AudioDriverAlsa ada;
    
    /* called before every test case */
    protected void setUp() {
        ada = new AudioDriverAlsa();
    }
    
    /* called after every test case */
    protected void tearDown() {
    }
    
    /* tests getting the available input devices */
    public void testGetAvailableInputDevices() {
        String[] inDevices = ada.getAvailableInputDevices();
        System.out.print("Input devices("+ inDevices.length + "): ");
        for (int i = 0; i < inDevices.length; i++) {
            System.out.print(inDevices[i] + ", ");
        }
        System.out.println();
        
        assertTrue(inDevices.length > 0);
    }
    
    /* tests getting the available output devices */
    public void testGetAvailableOutputDevices() {
        String[] outDevices = ada.getAvailableOutputDevices();
        System.out.print("Output devices("+ outDevices.length + "): ");
        for (int i = 0; i < outDevices.length; i++) {
            System.out.print(outDevices[i] + ", ");
        }
        System.out.println();
        
        assertTrue(outDevices.length > 0);
    }    
    
    /* test piping the data in from the java side */
    public void /*test*/PlayFromJava() {
        System.out.println("Start");
        
        // load the file into memory
        byte[] bytes = new byte[20000]; 
        try {
            FileInputStream fileInputStream = null;
            BufferedInputStream bufferedInputStream = null;
            fileInputStream = new FileInputStream("../AudioClips/Mono8k.au");
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            bufferedInputStream.read(bytes);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        // put the bytes into the ByteBuffer in multiple steps

        int numWrites = 100;
        for (int i = 0; i < numWrites; i++) {
	    try {
                ada.writeSpeaker(bytes, 
                                 bytes.length * i/numWrites,
                                 bytes.length / numWrites);

            } catch (IOException ex) {
		System.out.println("writeSpeaker failed to write "
		    + (bytes.length / numWrites));
	    }
        }

        try {
            Thread.sleep(10); // wait a bit for it all to finish
        } catch (Exception ex) {}

        System.out.println("Stop");
    }

    /* test a loopback */
    public void testLoopBackThroughJava() {
        int rates[] = { 8000, 16000, 32000, 44100, 48000 };
        for (int h = 0; h < 5; h++) {
            
            System.out.println(rates[h]);
            
            int numLoops = 600;
            int length = 1000;
            byte[] data = new byte[length];
            for (int i = 0; i < numLoops; i++) {
                int res;

		try {
		    res = ada.readMic(data, 0, length);
		} catch (IOException e) {
		    System.out.println("readMic failed");
		    continue;
		};
		    
		try {
                    res = ada.writeSpeaker(data, 0, length);
                } catch (IOException e) {
		    System.out.println("writeSpeaker failed to write "
			+ length);
		    continue;
		}
            }
            
            ada.stop();
        }
    }
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(AudioDriverAlsaTest.class);
    }
}

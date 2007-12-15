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

import junit.framework.*;
import java.io.*;

public class AudioDriverMacTest extends TestCase {
    
    private AudioDriverMac adm;
    
    /* called before every test case */
    protected void setUp() {
        adm = new AudioDriverMac();
    }
    
    /* called after every test case */
    protected void tearDown() {
    }
    
    /* tests getting the available input devices */
    public void testGetAvailableInputDevices() {
        AudioDevice[] inDevices = adm.getAvailableInputDevices();
        System.out.print("Input devices("+ inDevices.length + "): ");
        for (int i = 0; i < inDevices.length; i++) {
            System.out.print(inDevices[i].conciseDescription()+ ", ");
        }
        System.out.println();
        
        assertTrue(inDevices.length > 0);
    }
    
    /* tests getting the available output devices */
    public void testGetAvailableOutputDevices() {
        AudioDevice[] outDevices = adm.getAvailableOutputDevices();
        System.out.print("Output devices("+ outDevices.length + "): ");
        for (int i = 0; i < outDevices.length; i++) {
            System.out.print(outDevices[i].conciseDescription()+ ", ");
        }
        System.out.println();
        
        assertTrue(outDevices.length > 0);
    }    
    
    /* tests that the input device is set */
    public void testForInputDevice() {
        System.out.println("Current Input Device: " + adm.currentInputDevice);
        assertTrue(adm.currentInputDevice != null);
    }
    
    /* tests that the output device is set */
    public void testForOutputDevice() {
        System.out.println("Current Output Device: " + adm.currentOutputDevice);
        assertTrue(adm.currentOutputDevice != null);
    }
    
    /* test the input sample rates */
    public void testGetSupportedInputSampleRates() {
        float[] sampleRates = adm.getSupportedInputSampleRates();
        System.out.print("Input sample rates: ");
        for (int i = 0; i < sampleRates.length; i++) {
            System.out.print(sampleRates[i] + ", ");
        }
        System.out.println();
        assertTrue(sampleRates.length > 0);
    }
    
    /* test the output sample rates */
    public void testGetSupportedOutputSampleRates() {
        float[] sampleRates = adm.getSupportedOutputSampleRates();
        System.out.print("Output sample rates: ");
        for (int i = 0; i < sampleRates.length; i++) {
            System.out.print(sampleRates[i] + ", ");
        }
        System.out.println();
        assertTrue(sampleRates.length > 0);
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
        adm.initialize(10000);
        adm.start(8000.f, 1, 2, 1, 2, 16,
                  8000.f, 1, 2, 1, 2, 16);

        int numWrites = 100;
        for (int i = 0; i < numWrites; i++) {
            int res = adm.writeSpeaker(bytes, 
                             bytes.length * i/numWrites,
                             bytes.length / numWrites);

            if (res == 0) { System.out.println("dropped: " + i); }
            try {
                Thread.sleep(11); // wait a bit and try again   
            } catch (Exception ex) {}
        }

        try {
            Thread.sleep(10); // wait a bit for it all to finish
        } catch (Exception ex) {}

        System.out.println("Stop");
    }

    /* test a loopback */
    public void testLoopBackThroughJava() {
        float rates[] = { 8000.f, 16000.f, 32000.f, 44100.f, 48000.f };
        for (int h = 0; h < 5; h++) {
            
            System.out.println(rates[h]);
            adm.initialize(500000);
            adm.start(rates[h], 2, 4, 1, 4, 16,
                      rates[h], 2, 4, 1, 4, 16);
            
            int numLoops = 600;
            int length = 1000;
            byte[] data = new byte[length];
            for (int i = 0; i < numLoops; i++) {
                int res = adm.readMic(data, 0, length);
                if (res == 0) {
                    System.out.println("No Data to Read " + i);
                } else {
                    res = adm.writeSpeaker(data, 0, length);
                    if (res == 0) {
                        System.out.println("Dropped " + i);
                    }
                }
            }
            
            adm.stop();
        }
    }
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(AudioDriverMacTest.class);
    }
}

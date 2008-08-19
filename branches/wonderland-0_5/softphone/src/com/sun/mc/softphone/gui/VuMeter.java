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

package com.sun.mc.softphone.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.LinkedList;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.sun.mc.softphone.common.Utils;
import com.sun.mc.softphone.media.Microphone;
import com.sun.mc.softphone.media.MediaManager;
import com.sun.mc.softphone.media.MediaManagerFactory;

import com.sun.voip.Logger;

/*
 * Created on August 25, 2005, 12:41 PM
 *
 * @author mw17785  with changes for softphone from jp1223
 */
public class VuMeter implements Runnable {

    public static final int BUFFER_HERTZ = 10;

    public static final int BYTES_PER_SAMPLE = 2;
    
    private JFrame jf;
    private Thread captureThread;
    private Therm therm;
    
    private int byteRatePerSecond;

    private int bufferSize;

    private static VuMeter vuMeter;

    private boolean initDone = false;

    private boolean enabled = false;

    private String monitorMicrophone;

    private VuMeter() {
    }

    /** Creates the singleton instance of VuMeter */

    public static VuMeter getInstance() {
	if (vuMeter != null) {
	    return vuMeter;
	}

	vuMeter = new VuMeter();
	return vuMeter;
    }

    public void init(int sampleRate, int channels) {
	byteRatePerSecond = sampleRate * channels * BYTES_PER_SAMPLE;

	synchronized(dataBuffers) {
	    resetDataBuffers();
            bufferSize = byteRatePerSecond / BUFFER_HERTZ;
	}

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    Logger.println("sampleRate " + sampleRate +
	        " channels " + channels + " bufferSize " + bufferSize);
	}

	if (initDone) {
	    return;
	}

        initGui();

        captureThread = new Thread(this);
        captureThread.start();

	initDone = true;
	enabled = true;
    }

    public void setVisible(boolean isVisible) {
	jf.setVisible(isVisible);

	if (isVisible) {
            monitorMicrophone = Utils.getPreference(
                "com.sun.mc.softphone.media.MONITOR_MICROPHONE");

            if (monitorMicrophone == null || monitorMicrophone.length() == 0) {
                monitorMicrophone = "false";
            }

            Utils.setPreference("com.sun.mc.softphone.media.MONITOR_MICROPHONE",
                "true");

	    enabled = true;
	} else {
	    enabled = false;

	    synchronized(dataBuffers) {
	        resetDataBuffers();
	    }

	    if (monitorMicrophone != null) {
	        Utils.setPreference("com.sun.mc.softphone.media.MONITOR_MICROPHONE",
		    monitorMicrophone);
	    }
	}
    }

    public void initGui() {
        jf = new JFrame("VU Meter 0%");
        therm = new Therm();
	therm.setPreferredSize(new Dimension(200,200));
	therm.setMinimumSize(new Dimension(200,200));
        therm.setBorder(BorderFactory.createEtchedBorder());

        Box b = Box.createHorizontalBox();
        b.add(Box.createHorizontalStrut(10));
        b.add(therm);
        b.add(Box.createHorizontalStrut(10));
        jf.getContentPane().add(b);
        jf.pack();
        jf.setDefaultCloseOperation(jf.DO_NOTHING_ON_CLOSE);
        jf.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent evt) {
                setVisible(false);
            }
        });
    }
    
    private LinkedList dataBuffers = new LinkedList();

    private byte[] currentData;
    private int currentOffset;

    private void resetDataBuffers() {
	synchronized(dataBuffers) {
	    dataBuffers.clear();

	    currentData = null;
	    currentOffset = 0;
	}
    }

    /*
     * Microphone readers send the microphone data here.
     * Data is accumulated until there is enough to fill
     * a buffer of size bufferSize.
     */
    public void data(byte[] data) {
	if (enabled == false) { 
	    return;
	}

	synchronized(dataBuffers) {
	    int length = data.length;
	    int offset = 0;

	    while (length > 0) {
	        if (currentData == null) {
	            currentData = new byte[bufferSize];
	            currentOffset = 0;
	        }

	        while (currentOffset < bufferSize && length > 0) {
	            currentData[currentOffset] = data[offset];

		    currentOffset++;
		    offset++;
	            length--;
	        }
	
	        if (currentOffset < bufferSize) {
		    break;
	        }

	    	dataBuffers.add(currentData);
		currentData = null;
		currentOffset = 0;

		/*
		 * No need to keep more than half a second's worth
		 */
		if (dataBuffers.size() > 25) {
		    if (Logger.logLevel >= Logger.LOG_MOREINFO) {
		        Logger.println("VuMeter dropping first data buffer");
		    }
		    dataBuffers.remove(0);
		}

	        synchronized(this) {
	            notifyAll();
	        }
	    }
	}
    }

    public void run() {
        while (captureThread == Thread.currentThread()) {
	    synchronized(this) {
	        try {
		    wait();
		} catch (InterruptedException e) {
		}
	    }
            
	    byte[] buffer;

	    synchronized(dataBuffers) {
		buffer = (byte[]) dataBuffers.remove(0);
	    }
		
            if (jf != null) {
                int tm = (int) (bufferSize * 10 / byteRatePerSecond);
                int min = tm / 10 / 60;
                int sec = (tm / 10) % 60;
                int frac = tm % 10;

                String mins = 
		    (min<10)? ("0"+String.valueOf(min)) : String.valueOf(min);
                String secs = 
		    (sec<10)? ("0"+String.valueOf(sec)) : String.valueOf(sec);
                int max = 0;

                for (int i = 0; i < bufferSize; i += 8) {
                    int val = ((int)buffer[i]<<8) + ((int)buffer[i+1] & 0xff);
                    if (val>max) {
                        max = val;
                    }
                }
                therm.setValue(max/32768.0f);
            }
        }
    }
    
    class Therm extends JPanel {
        double value;
        double max;
        long maxtimeout = 0;
        
        public void setValue(double value) {
            this.value = (double)Math.sqrt(value);
            repaint();
        }
        
        public void paint(Graphics g) {
            g.setColor(getBackground());
            int w = getWidth();
            int h = getHeight();
            g.fillRect(0, 0, w, h);
            g.setColor(Color.blue);
            int v = (int)(h*value);
            g.fillRect(0, h-v, w, v);
            long now = System.currentTimeMillis();
            if (now > maxtimeout) {
                max = 0;
            }
            if (value > max) {
                max = value;
                maxtimeout = now+1000;
            }

	    jf.setTitle("Vu Meter " + ((int) (max * 100)) + "%");
            int mh = (int)(h*max);
            g.setColor(Color.red);
            g.drawLine(0, h-mh-1, w, h-mh-1);
        }
    }

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 2;

    public static void main(String[] args) {
        VuMeter vuMeter = VuMeter.getInstance();

        vuMeter.init(SAMPLE_RATE, CHANNELS);

        vuMeter.setVisible(true);

        vuMeter.test();
    }

    private void test() {
        Microphone microphone = null;

        try {
            MediaManager mediaManager = MediaManagerFactory.getInstance();
            microphone = mediaManager.getMicrophone(16000, 2);
        } catch (IOException e) {
            Logger.println("Can't open microphone:  "
                + e.getMessage());
            System.exit(1);
        }

        Logger.println("bufferSize " + bufferSize +
            " mic buf size " + microphone.getBufferSize());

        byte[] buffer = new byte[bufferSize];

	try {
            while (true) {
                microphone.read(buffer, 0, buffer.length);

                data(buffer);
            }
	} catch (IOException e) {
	    Logger.println("Unable to read microphone!  "
		+ e.getMessage());
	}
    }

}

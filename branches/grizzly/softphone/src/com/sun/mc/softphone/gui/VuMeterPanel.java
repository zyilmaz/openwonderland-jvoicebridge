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

import com.sun.mc.softphone.common.Utils;
import com.sun.mc.softphone.media.Microphone;
import com.sun.mc.softphone.media.MicrophoneListener;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.io.IOException;
import javax.swing.ImageIcon;
import javax.swing.JPanel;

/**
 *
 * @author mw17785
 */
public class VuMeterPanel extends JPanel implements MicrophoneListener {
    public static final int ENABLED = 1;
    public static final int DISABLED = 0;
    public static final int STANDBY = 2;
    private float value = 0;
    private float max = -1;
    private long maxTimeout = 0;
    private Microphone mic;
    private int dataSize = 2;  // # of bytes per sample
    private int channels = 1;  // # of channels
    private float rate;          // sample rate
    private Image img;
    private Font font = new Font("Sans-serif", Font.PLAIN, 9);
//    private boolean enabled;
    private int state = DISABLED;   // ENABLED, DISABLED, STANDBY
    
    public VuMeterPanel() {
        ImageIcon mtr = new ImageIcon(Utils.getResource("meter.png"));
        img = mtr.getImage();
        setPreferredSize(new Dimension(img.getWidth(null), img.getHeight(null)));
    }
    
    public void paint(Graphics g1) {
        Graphics2D g = (Graphics2D)g1;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (state == ENABLED) {
            g.setColor(Color.black);
        } else {
            g.setColor(Color.lightGray);
        }
        g.drawImage(img, 0, 0, null);
        int val = (int)(value*100);
        String num = String.valueOf(val);
        g.setFont(font);
        g.drawString(num, 38, 42);
        float ang = (value-0.5f)*0.78f;
        float cos = (float)Math.cos(ang);
        float sin = (float)Math.sin(ang);
        float ctrx = 48f;
        float ctry = 100;
        float r1 = ctry-27;
        float r2 = ctry-12;
        float x1 = (float)(ctrx + r1*sin);
        float x2 = (float)(ctrx + r2*sin);
        float y1 = (float)(ctry - r1*cos);
        float y2 = (float)(ctry - r2*cos);
        GeneralPath gp = new GeneralPath();
        gp.moveTo(x1+cos*2, y1+sin*2);
        gp.lineTo(x2, y2);
        gp.lineTo(x1-cos*2, y1-sin*2);
        gp.closePath();
        g.fill(gp);
        if (state == STANDBY) {
            g.setColor(Color.red);
            g.drawString("Configuring...", 17, 26);
        }
        if (state == ENABLED) {
            ang = (max-0.5f)*0.78f;
            cos = (float)Math.cos(ang);
            sin = (float)Math.sin(ang);
            r1 = ctry - 12;
            r2 = ctry - 8;
            gp.reset();
            gp.moveTo(ctrx+r1*sin, ctry-r1*cos);
            gp.lineTo(ctrx+r2*sin, ctry-r2*cos);
            g.setColor(Color.blue);
            g.draw(gp);
        }
    }
    
    public void setValue(float value) {
        long now = System.currentTimeMillis();
        if (value<0) {
            value = 0;
        } else if (value>1) {
            value = 1;
        }
        this.value = value;
        if (value > max || now > maxTimeout) {
            maxTimeout = now+1000;
            max = value;
        }
        repaint();
    }
    
    public void setState(int state) {
        this.state = state;
        repaint();
    }

    public void start(Microphone inmic) {
	System.out.println("Start called...");
        this.mic = inmic;
        dataSize = mic.getSampleSizeInBits()/8;
        channels = mic.getChannels();
        rate = mic.getSampleRate();
        state = ENABLED;
        mic.addListener(this);
        repaint();
    }
    
    public void stop() {
	if (mic != null) {
            mic.removeListener(this);
	}
        state = DISABLED;
        setValue(0);
    }
    
    public void microphoneData(byte[] linearData, int offset, int length) {
	System.out.println("got data!  offset " + offset + " length " 
	    + length + " state " + state);

        if (state != ENABLED) {
            return;
        }
        long ourmax = 0;
        //System.out.prinltn("Got "+length+" bytes of microphone data");
        for (int i=offset; i<offset+length; i+= dataSize*channels) {
            long sample = ((long)linearData[i]<<8) + ((long)linearData[i+1] & 0xFF);
            sample = Math.abs(sample);
            if (sample > ourmax) {
                ourmax = sample;
            }
        }
        setValue((float)((double)ourmax / (double)(1<<15)));
    }
    
}

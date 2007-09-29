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

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.event.*;

import java.io.IOException;

import com.sun.mc.softphone.common.Utils;
import com.sun.mc.softphone.media.Speaker;
import com.sun.mc.softphone.media.MediaManager;
import com.sun.mc.softphone.media.MediaManagerFactory;

public class LpfControl implements LpfPanelListener {

    private JFrame jf;
    private LpfPanel lp;

    private MediaManager mediaManager;

    public LpfControl(String title) {
        lp = new LpfPanel();
        lp.setLpfPanelListener(this);
        buildGUI();

	int nAvg = 0;

	String s = Utils.getPreference("com.sun.mc.softphone.media.nAvg");

	if (s != null && s.length() > 0) {
	    try {
	        nAvg = Integer.parseInt(s);
	    } catch (NumberFormatException e) {
	    }
	}
	
        mediaManager = MediaManagerFactory.getInstance();

	sourceAdded(new LpfParameters(nAvg, mediaManager.getSpeakerVolume()));
    }

    public synchronized void sourceAdded(LpfParameters lpfParameters) {
        lp.addMember(lpfParameters);
    }
    
    public synchronized void sourceRemoved(LpfParameters lpfParameters) {
        lp.removeMember(lpfParameters);
    }

    public void lpfParametersChanged(LpfParameters lpfParameters) {
	Utils.setPreference(
	    "com.sun.mc.softphone.media.nAvg",
	    String.valueOf(lpfParameters.nAvg));

        mediaManager.setSpeakerVolume(lpfParameters.volume);
    }

    private void buildGUI() {
        jf = new JFrame("Low Pass Filter Parameters");
        
        jf.getContentPane().add(lp, BorderLayout.CENTER);
        
        JLabel avgLabel = new JLabel("samples to average");
        JPanel avgPanel = new JPanel();
        avgPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        avgPanel.add(avgLabel);
        jf.getContentPane().add(avgPanel, BorderLayout.SOUTH);
        
        JPanel LpfPanel = new JPanel() {
            public void paint(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(Color.LIGHT_GRAY);
                g2.fillRect(0, 0, getWidth(), getHeight());
           
                // g2.setTransform(AffineTransform.getRotateInstance(Math.PI / 2));
                g2.drawString("Low Pass Filter Parameters", 0, 0);
            }
        };

        avgPanel.setPreferredSize(new Dimension(16, 0));
        
        jf.setSize(600, 200);
        jf.setVisible(true);

	jf.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    }

    public void setVisible(boolean isVisible) {
	jf.setVisible(isVisible);
    }

}

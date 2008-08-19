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

import java.util.Vector;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;

public class PCPanel extends JLayeredPane
    implements MouseListener, MouseMotionListener
{
    private PCLabel pCLabel;
    
    private boolean dragging;
    
    private JPanel bgPanel;
    
    public PCPanel(final Parameter param) {
        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent evt) {
		if (pCLabel == null) {
	            pCLabel = new PCLabel(
			getWidth() - 10, getHeight() - 10, param);
	            pCLabel.addMouseListener(PCPanel.this);
	            pCLabel.addMouseMotionListener(PCPanel.this);

	            add(pCLabel, JLayeredPane.DRAG_LAYER);
   
	            setLayout(null);

	            bgPanel = new BGPanel();
	            bgPanel.setBounds(0, 0, getWidth(), getHeight());
	            add(bgPanel, JLayeredPane.DEFAULT_LAYER);
		}

		int w = getWidth();
		int h = getHeight();

		//System.out.println(
		//    "Panel Size w = " + getWidth() + " h = " + getHeight());

                bgPanel.setBounds(0, 0, w, h);
                pCLabel.sizeChanged(w, h);
            }
        });
    }
    
    private Vector listeners = new Vector();

    public void addPCPanelListener(PCPanelListener listener) {
	synchronized(listeners) {
            listeners.add(listener);
	}
    }
    
    public void removePCPanelListener(PCPanelListener listener) {
	synchronized(listeners) {
            listeners.remove(listener);
	}
    }

    public void mouseEntered(MouseEvent e) {
        if (!dragging) {
            repaint();
        }
    }

    public void mouseExited(MouseEvent e) {
        if (!dragging) {
            repaint();
        }
    }
    
    public void mouseDragged(MouseEvent e) {
        int x = ((PCLabel) e.getSource()).getX();
        int y = ((PCLabel) e.getSource()).getY();
            
        pCLabel.moveTo(x + e.getX(), y + e.getY());
        repaint();

        // notify listeners
        synchronized(listeners) {
            for (int i = 0; i < listeners.size(); i++) {
                PCPanelListener listener = (PCPanelListener) listeners.get(i);

		Point location = pCLabel.getLocation();

		location.setLocation(location.getX(), 
		    getHeight() - location.getY());
		
                listener.locationChanged(location);
            }
        }
    }

    public void mouseMoved(MouseEvent e) {
        // ignore
    }
    
    public void mouseClicked(MouseEvent e) {
        // ignore
    }

    public void mousePressed(MouseEvent e) {
        dragging = true;
    }

    public void mouseReleased(MouseEvent e) {
        dragging = false;
        
        repaint();
    }
    
    String parameters;

    public void setString(String s) {
	parameters = s;
	//bgPanel.paint(getGraphics());
    }

    /** 
     * Draw the background
     */
    class BGPanel extends JPanel {
        public void paint(Graphics g) {
            int height = getHeight();
            int width = getWidth();
       
            // fill with white
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            
            // draw horizontal lines
            g.setColor(Color.RED);
            for (int i = 1; i < 10; i++) {
                double heightFactor = (double)i / 10;
                int lineHeight = height - ((int) (heightFactor * height));
                g.drawLine(0, lineHeight, width, lineHeight);
            }
            
            // write the coordinates if dragging

	    String s = parameters;

	    if (s == null) {
	        Point p = pCLabel.getLocation();

                s = "(" + (p.getX() - 10) + "," 
		    + (getHeight() - 10 - p.getY()) + ")";
	    }
                 
            g.drawString(s, 3, height - 3);
        }
    }

}

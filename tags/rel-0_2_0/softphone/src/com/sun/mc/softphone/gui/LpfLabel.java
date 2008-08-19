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
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import javax.swing.JLabel;
import javax.swing.border.AbstractBorder;

import com.sun.mc.softphone.common.Utils;

public class LpfLabel extends JLabel {
    int parentWidth;
    int parentHeight;
    
    private LpfParameters lpfParameters;

    /** Creates a new instance of LpfLabel */
    public LpfLabel(LpfParameters lpfParameters) {
        super ("X");
       
        setBorder(new HotSpotBorder());

	String s = Utils.getPreference("com.sun.mc.softphone.media.nAvg");
	
	if (s != null) {
	    try {
		lpfParameters.nAvg = Integer.parseInt(s);
	    } catch (NumberFormatException e) {
	    }
	}

	if (lpfParameters.nAvg > 50) {
	    lpfParameters.nAvg = 50;
	}

	setLpfParameters(lpfParameters);
    }
    
    public void sizeChanged(int parentWidth, int parentHeight) {
        this.parentWidth = parentWidth;
        this.parentHeight = parentHeight;
       
        Point p = lpfParametersToLocation(lpfParameters);
        int width = getPreferredSize().width;
        int height = getPreferredSize().height;
        int x = p.x - (width / 2);
        int y = p.y - height + 16;
        
        setBounds(x, y, width, height);
    }
    
    public LpfParameters getLpfParameters() {
        return lpfParameters;
    }
    
    public void setLpfParameters(LpfParameters lpfParameters) {
        this.lpfParameters = lpfParameters;
        
        Point p = lpfParametersToLocation(lpfParameters);
        int x = p.x - (getWidth() / 2);
        int y = p.y - getHeight() + 16;
    
        setLocation(x, y);
    }
    
    public void moveTo(int x, int y) {
if (false) {
	if (x < 0) {
	    x = 0;
	}

	if (x > parentWidth) {
	    x = parentWidth;
	}

	if (y < 0) {
	    y = 0;
	}

	if (y > parentHeight) {
	    y = parentHeight;
	}
}
	    
        setLocation(x, y);
    
        lpfParameters = locationToLpfParameters(getHotX(), getHotY());
    }
    
    public int getHotX() {
        return getX() + (getWidth() / 2);
    }
    
    public int getHotY() {
        return getY() + getHeight() - 16;
    }
    
    private LpfParameters locationToLpfParameters(int x, int y) {
        // calculate the total volume based on the height as a percentage
        // of the total height.
        double heightFactor = ((double) (parentHeight - y)) / parentHeight;
        double volume = ((double) Math.pow(10f, heightFactor) - 1);
       
	int nAvg = (int) Math.round(((double)x / parentWidth) * 50);

	if (nAvg < 0) {
	    nAvg = 0;
	}

	if (nAvg > 50) {
	    nAvg = 50;
	}

        return new LpfParameters(nAvg, volume);
    }
    
    private Point lpfParametersToLocation(LpfParameters lpfParameters) {
	double volume = lpfParameters.volume;

        double volumeFactor = (double) 
	    (Math.log((volume + 2) / 2f) / Math.log(10));

        int y = parentHeight - (int) Math.round(volumeFactor * parentHeight);
        
	int x = (int) (lpfParameters.nAvg / 50.0F * parentWidth);
        return new Point(x, y);
    }
    
    public boolean equals(Object o) {
        return (o != null && o instanceof LpfLabel);
    }
    
    class HotSpotBorder extends AbstractBorder {
        public Insets getBorderInsets(Component c) {
            return new Insets(0, 0, 16, 0);
        }
        
        public Insets getBorderInsets(Component c, Insets insets) {
            Insets mine = getBorderInsets(c);
            
            insets.top = mine.top;
            insets.left = mine.left;
            insets.bottom = mine.bottom;
            insets.right = mine.right;
            
            return insets;
        }
        
        public boolean isBorderOpaque() {
            return true;
        }
        
        public void paintBorder(Component c, Graphics g, int x, int y, 
                                int width, int height) 
        {
            int centerX = width / 2;
            int centerY = height / 2;
            
            g.setColor(Color.RED);
            g.fillOval(centerX - 2, centerY - 2, 4, 4);
        }        
    }

}

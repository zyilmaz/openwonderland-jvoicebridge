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

package com.sun.mc.softphone.gui.config;

import javax.swing.*;
import java.awt.Toolkit;

public class MediaConfigurationFrame extends JFrame {

    MediaConfigurationDialog configurationDialog;

    public MediaConfigurationFrame() {
	if (configurationDialog != null) {
	    configurationDialog.setVisible(true);
	    return;
	}

        configurationDialog = new MediaConfigurationDialog(this);

        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        Toolkit toolkit = Toolkit.getDefaultToolkit();

        setLocation (
	    ((int)toolkit.getScreenSize().getWidth() - getWidth()) / 2,
            ((int)toolkit.getScreenSize().getHeight() - getHeight()) / 2);

        pack();
        setVisible(true);
    }

    public void addListener(MediaConfigDoneListener listener) {
	configurationDialog.addListener(listener);
    }

}

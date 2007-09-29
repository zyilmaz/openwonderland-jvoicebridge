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

import com.sun.mc.softphone.common.Utils;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author Damian Minkov
 * @version 1.0
 */

public class ConfigTest extends JFrame {
    public ConfigTest() {
        ConfigurationPanel pane = new ConfigurationPanel(this);
        this.setTitle(pane.getTitle());
        this.getContentPane().add(pane);
    }

    public static void main(String[] args) {
        //Utils.initPreferences();

        ConfigTest configTest = new ConfigTest();
        configTest.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        configTest.setSize(640, 400);
        //center
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        configTest.setLocation (
                  ((int)toolkit.getScreenSize().getWidth() - configTest.getWidth()) / 2,
                  ((int)toolkit.getScreenSize().getHeight() - configTest.getHeight()) / 2);

        configTest.pack();
        configTest.setVisible(true);
    }

}

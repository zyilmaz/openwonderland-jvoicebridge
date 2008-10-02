/*
 * MissedListModel.java  (2004)
 *
 * Copyright (c) 2005 Sun Microsystems, Inc. All rights reserved.
 * 
 * Unpublished - rights reserved under the Copyright Laws of the United States.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this document. In
 * particular, and without limitation, these intellectual property rights may
 * include one or more of the U.S. patents listed at http://www.sun.com/patents
 * and one or more additional patents or pending patent applications in the
 * U.S. and in other countries.
 * 
 * SUN PROPRIETARY/CONFIDENTIAL.
 * 
 * U.S. Government Rights - Commercial software. Government users are subject
 * to the Sun Microsystems, Inc. standard license agreement and applicable
 * provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties. Sun, Sun
 * Microsystems, the Sun logo, Java, Jini, Solaris and Sun Ray are trademarks
 * or registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries, exclusively
 * licensed through X/Open Company, Ltd.
 */

package com.sun.mc.softphone.gui;

import java.io.*;
import javax.swing.*;

import com.sun.mc.softphone.common.Utils;

public class MissedListModel
    extends DefaultListModel
{
    public MissedListModel()
    {
        loadMissedHistory();
    }

    private static String getFilename()
    {
       if ( File.separatorChar == '/' )
          return Utils.getProperty("user.home") + 
                  "/.sip-communicator/missedhistory.txt";

      return "missedhistory.txt";
    }

    private void loadMissedHistory()
    {
        try {
            FileReader fReader = new FileReader( getFilename() );
            BufferedReader dialHistory = new BufferedReader(fReader);
            String entry = null;
            while ( (entry = dialHistory.readLine()) != null) {
                super.addElement(entry);
            }
            dialHistory.close();
            fReader.close();
        }
        catch (IOException ex) {
            //We don't really care
            //alert silently the user
        }
    }

    public void addElement(Object element)
    {
        // ignore if empty
        if (element == null || element.toString().trim().length() == 0) {
            return;
        }

        // if already exists, then remove it (will get re added to beginning)
        if (super.indexOf(element) != -1) {
            super.removeElementAt(super.indexOf(element));
        }

        // only keep last 16
        if(super.getSize()>=16) {
            super.removeElementAt(15);
        }

        super.insertElementAt(element, 0);

        try {
            FileWriter fWriter = new FileWriter(getFilename(), false);
            PrintWriter dialHistory = new PrintWriter(fWriter);
            for(int i=0;i<super.getSize();i++) {
                dialHistory.print(super.getElementAt(i).toString()+"\r\n");
            }
            dialHistory.close();
            fWriter.close();
        }
        catch (IOException ex) {
            //We don't really care
            //alert silently the user
            //ex.printStackTrace();
        }
    }
}

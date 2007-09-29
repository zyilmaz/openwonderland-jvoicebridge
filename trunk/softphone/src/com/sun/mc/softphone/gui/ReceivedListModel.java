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

import java.io.*;
import javax.swing.*;

import com.sun.mc.softphone.common.Utils;

public class ReceivedListModel
    extends DefaultListModel
{
    public ReceivedListModel()
    {
        loadReceivedHistory();
    }

    private static String getFilename()
    {
       if ( File.separatorChar == '/' )
          return Utils.getProperty("user.home") + 
                  "/.sip-communicator/receivedhistory.txt";

      return "receivedhistory.txt";
    }

    private void loadReceivedHistory()
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
        } catch (IOException ex) {
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
        } catch (IOException ex) {
            //We don't really care //alert silently the user
            //ex.printStackTrace();
	}
    }
}

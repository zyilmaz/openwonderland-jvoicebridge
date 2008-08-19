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

import java.util.*;

class Lines
{

    public static final int DEFAULT_MAX_LINES = 4;
    private int maxLines = DEFAULT_MAX_LINES;
    private InterlocutorUI interlocutors[];

    public Lines() {
        // Read a property for maximum number of lines.  This allows
        // the load generator to work properly
        String maxLinesProp = 
                System.getProperty("com.sun.mc.softphone.gui.Lines.MAX_LINES");
        if (maxLinesProp != null) {
            maxLines = Integer.parseInt(maxLinesProp);
        }
        
        interlocutors = new InterlocutorUI[maxLines];
    }
    
    public int getFreeLine() {
        for(int i=0;i<maxLines;i++) {
            if(interlocutors[i]==null) return i;
        }

	System.out.println("No free lines!");
        return -1;
    }

    public int addInterlocutor(InterlocutorUI interlocutor)
    {
        int i=getFreeLine();
        if(i>=0) {
            addInterlocutor(interlocutor,i);
        }
        return i;
    }

    public void addInterlocutor(InterlocutorUI interlocutor,int index)
    {
        interlocutors[index]=interlocutor;
    }

    public void removeInterlocutor(int id)
    {
        for (int i = 0; i < maxLines; i++) {
            if(interlocutors[i]!=null) {
                if(interlocutors[i].getID()==id) {
                    interlocutors[i]=null;
                }
            }
        }
    }

    private static final int NAME_COLUMN_INDEX = 0;
    private static final int ADDRESS_COLUMN_INDEX = 1;
    private static final int CALL_STATUS_COLUMN_INDEX = 2;
    private static final int CALL_ERROR_COLUMN_INDEX = 3;
    private final String[] columnNames = {
        "Name",
        "Address",
        "Call Status"};

    public int getColumnCount()
    {
        return columnNames.length;
    }

    public int getRowCount()
    {
        return maxLines;
    }

    public String getColumnName(int col)
    {
        return columnNames[col];
    }

    public Object getValueAt(int row, int col)
    {
        Object res;
        if (row >=maxLines) {
            return "";
        }

        InterlocutorUI interlocutor = interlocutors[row];
        if(interlocutor==null) {
            return "";
        }

        switch (col) {
            case NAME_COLUMN_INDEX:
                res = interlocutor.getName();
                break;
            case ADDRESS_COLUMN_INDEX:
                res = interlocutor.getAddress();
                break;
            case CALL_STATUS_COLUMN_INDEX:
                res = interlocutor.getCallState();
                break;
            case CALL_ERROR_COLUMN_INDEX:
                res = interlocutor.getCallError();
                break;
            default:
                throw new IndexOutOfBoundsException("There is no column " +
                    new Integer(col).toString());
        }
        return res == null ? "" : res;
    }

    /*
     * JTable uses this method to determine the default renderer/
     * editor for each cell.  If we didn't implement this method,
     * then the last column would contain text ("true"/"false"),
     * rather than a check box.
     */
    public Class getColumnClass(int c)
    {
        return getValueAt(0, c).getClass();
    }

    /*
     * Don't need to implement this method unless your table's
     * editable.
     */
    public boolean isCellEditable(int row, int col)
    {
        return false;
    }

    private void printDebugData()
    {
        int numCols=getColumnCount();
        for (int i = 0; i < maxLines; i++) {
            System.out.print("    row " + i + ":");
            for (int j = 0; j < numCols; j++) {
                System.out.print("  " + getValueAt(i, j));
            }
            System.out.println();
        }
        System.out.println("--------------------------");
    }

    InterlocutorUI getInterlocutorAt(int row)
    {
        return (interlocutors[row]);
    }

    private void updateInterlocutorStatus(int interID)
    {
        int index = findIndex(interID);
//        fireTableRowsUpdated(index, index);
    }

    int findIndex(int id)
    {
        for (int i = 0; i < maxLines; i++) {
            if(interlocutors[i]!=null) {
                if ( interlocutors[i].getID() == id) {
                    return i;
                }
            }
        }
        return -1;
    }

//----------------------------- GUI Callback ---------------------------
    public void update(InterlocutorUI interlocutorUI)
    {
        updateInterlocutorStatus(interlocutorUI.getID());
    }

    public void remove(InterlocutorUI interlocutorUI)
    {
        removeInterlocutor(interlocutorUI.getID());
    }
}

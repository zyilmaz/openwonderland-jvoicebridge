/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2000 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * Portions of this software are based upon public domain software
 * originally written at the National Center for Supercomputing Applications,
 * University of Illinois, Urbana-Champaign.
 *
 * Copyright 2007 Sun Microsystems, Inc.
 */
package com.sun.mc.softphone.gui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*; //import com.borland.jbcl.layout.*;
import javax.swing.table.*;

/**
 * <p>Title: SIP COMMUNICATOR</p>
 * <p>Description:JAIN-SIP Audio/Video phone application</p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Organisation: LSIIT laboratory (http://lsiit.u-strasbg.fr) </p>
 * <p>Network Research Team (http://www-r2.u-strasbg.fr))</p>
 * <p>Louis Pasteur University - Strasbourg - France</p>
 * @author Emil Ivov (http://www.emcho.com)
 * @version 1.1
 *
 */
public class ConfigFrame
    extends JFrame
{
    BorderLayout borderLayout1 = new BorderLayout();
    JPanel buttonsPane = new JPanel();
    JButton cancelButton = new JButton();
    JButton saveButton = new JButton();
    Border border1;
    Border border2;
    Border border3;
    Border border4;
    Border border5;
    Border border6;
    Border border7;
    BorderLayout borderLayout3 = new BorderLayout();
    AbstractTableModel tableModel = new PropertiesTableModel();
    PropertiesTableModel properties = new PropertiesTableModel();
//    VerticalFlowLayout verticalFlowLayout1 = new VerticalFlowLayout();
    Border border8;
    TitledBorder titledBorder1;
    Border border9;
    JTable propertiesTable = new JTable();
    JScrollPane propertiesPane = new JScrollPane();
    JPanel labelPanel = new JPanel();
    JLabel propsAreOptionalLabel = new JLabel();
    Border border10;
    public ConfigFrame()
    {
        try {
            jbInit();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        propertiesTable.setModel(properties);
        this.pack();
	setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    }

    private void jbInit() throws Exception
    {
        border1 = BorderFactory.createEmptyBorder(10, 4, 10, 4);
        border2 = BorderFactory.createEmptyBorder(0, 0, 0, 3);
        border3 = BorderFactory.createCompoundBorder(new EtchedBorder(
            EtchedBorder.RAISED, GuiManager.defaultBackground, new Color(156, 156, 158)),
            BorderFactory.createEmptyBorder(2, 10, 2, 10));
        border4 = BorderFactory.createCompoundBorder(new TitledBorder(
            BorderFactory.createEtchedBorder(GuiManager.defaultBackground,
                                             new Color(156, 156, 158)),
            " Registrar "), BorderFactory.createEmptyBorder(10, 5, 10, 0));
        border6 = BorderFactory.createCompoundBorder(new TitledBorder(
            BorderFactory.createEtchedBorder(GuiManager.defaultBackground,
                                             new Color(156, 156, 158)),
            " Proxy "), BorderFactory.createEmptyBorder(10, 5, 10, 0));
        border7 = BorderFactory.createCompoundBorder(new TitledBorder(
            BorderFactory.createEtchedBorder(GuiManager.defaultBackground,
                                             new Color(156, 156, 158)),
            " User Details "), BorderFactory.createEmptyBorder(10, 5, 10, 0));
        border5 = BorderFactory.createEmptyBorder(5, 5, 5, 7);
        border8 = BorderFactory.createEtchedBorder(GuiManager.defaultBackground,
            new Color(156, 156, 158));
        titledBorder1 = new TitledBorder(border8, " Media Details ");
        border9 = BorderFactory.createCompoundBorder(new TitledBorder(
            BorderFactory.createEtchedBorder(GuiManager.defaultBackground,
                                             new Color(156, 156, 158)),
            " Media Details "), BorderFactory.createEmptyBorder(10, 5, 10, 5));
        border10 = BorderFactory.createEmptyBorder(3, 0, 3, 0);
        saveButton.setMnemonic('S');
        saveButton.setText("Save");
        cancelButton.setMnemonic('C');
        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new
                                       ConfigFrame_cancelButton_actionAdapter(this));
        this.getContentPane().setLayout(borderLayout1);
        borderLayout3.setVgap(10);
        borderLayout3.setHgap(10);
        this.setResizable(false);
        this.setTitle("SipCommunicator - Configure");
        propertiesPane.setMinimumSize(new Dimension(503, 403));
        propertiesPane.setPreferredSize(new Dimension(503, 403));
        propsAreOptionalLabel.setForeground(UIManager.getColor(
            "OptionPane.questionDialog.border.background"));
        propsAreOptionalLabel.setText("All properties are OPTIONAL!");
        labelPanel.setBorder(border10);
        this.getContentPane().add(buttonsPane, BorderLayout.SOUTH);
        buttonsPane.add(saveButton, null);
        buttonsPane.add(cancelButton, null);
        this.getContentPane().add(propertiesPane, BorderLayout.CENTER);
        this.getContentPane().add(labelPanel, BorderLayout.NORTH);
        labelPanel.add(propsAreOptionalLabel, null);
        propertiesPane.setViewportView(propertiesTable);
    }

    void cancelButton_actionPerformed(ActionEvent e)
    {
        dispose();
    }
}

class ConfigFrame_cancelButton_actionAdapter
    implements java.awt.event.ActionListener
{
    ConfigFrame adaptee;
    ConfigFrame_cancelButton_actionAdapter(ConfigFrame adaptee)
    {
        this.adaptee = adaptee;
    }

    public void actionPerformed(ActionEvent e)
    {
        adaptee.cancelButton_actionPerformed(e);
    }
}

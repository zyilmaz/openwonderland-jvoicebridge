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
import com.sun.mc.softphone.gui.event.*;
import com.sun.mc.softphone.media.MediaManager;
//import com.sun.mc.softphone.media.JMFRegistry;

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
public interface GuiManagerUI {

    public void showConfigFrame();

    public void showLineTest(MediaManager mediaManager);

    public void showNetworkInterfaceConfig();
    
    public void showPhoneFrame(boolean show);

    public void addVisualComponent(Component vComp);

    public void addControlComponent(Component cComp);

    public void removePlayerComponents();

    public void addInterlocutor(InterlocutorUI interlocutor);

    public void setCommunicationActionsEnabled(boolean enabled);

    public void addUserActionListener(UserActionListener l);

    public void removeUserActionListener(UserActionListener l);

    public void startAlert(String alertResourceName);

    public void stopAlert(String alertResourceName);

    public boolean startDtmf(String key);

    public boolean stopDtmf(String key);

    public void muted(boolean isMuted);

    public void setGlobalStatus(String statusCode, String reason);

    public void requestAuthentication(String realm,
                                      String userName,
                                      String authenticationUserName,
                                      char[] password);

    public String getUserName();

    public String getAuthenticationUserName();

    public char[] getAuthenticationPassword();

    public void setPhoneNumber(String phoneNumber);

}
/**
 * Open Wonderland
 *
 * Copyright (c) 2011, Open Wonderland Foundation, All Rights Reserved
 *
 * Redistributions in source code form must reproduce the above
 * copyright and this condition.
 *
 * The contents of this file are subject to the GNU General Public
 * License, Version 2 (the "License"); you may not use this file
 * except in compliance with the License. A copy of the License is
 * available at http://www.opensource.org/licenses/gpl-license.php.
 *
 * The Open Wonderland Foundation designates this particular file as
 * subject to the "Classpath" exception as provided by the Open Wonderland
 * Foundation in the License file that accompanied this code.
 */
package com.sun.mc.softphone.sip;

import com.sun.mc.softphone.common.Utils;
import com.sun.voip.sip.security.SipSecurityManager;
import java.text.ParseException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;

/**
 * Extension of Sip security manager for the client
 * @author Jonathan Kaplan <jonathankap@gmail.com>
 */
public class SipClientSecurityManager extends SipSecurityManager {
    private final SipManager sipManager;
    
    public SipClientSecurityManager(SipManager sipManager) {
        this.sipManager = sipManager;
    }
    
    @Override
    protected void updateReoriginatedRequest(Request reoriginatedRequest) throws ParseException {
        FromHeader from = (FromHeader) 
                reoriginatedRequest.getHeader(FromHeader.NAME);
        Address add = from.getAddress();
        SipURI sipUri = (SipURI) add.getURI();
        
        if (reoriginatedRequest.getMethod().equals(Request.REGISTER)) {
            ToHeader to =
                    (ToHeader) reoriginatedRequest.getHeader(ToHeader.NAME);

            String proxyWorkAround =
                    Utils.getProperty("com.sun.mc.softphone.REGISTRAR_WORKAROUND");

// System.out.println("proxyWorkAround="+proxyWorkAround);

            if (proxyWorkAround != null
                    && proxyWorkAround.toUpperCase().equals("TRUE")) {

                /*
                 * Some registrars (proxies) require the To Header 
                 * to be addressed to them for the REGISTER command
                 * even though the SIP Spec explicitly says that the
                 * To header must contain the address being registered.
                 * This is a workaround so that registration works.
                 */
                String registrarAddress = sipManager.getRegistrarAddress();
                int registrarPort = sipManager.getRegistrarPort();

                String registrarTransport = Utils.getPreference(
                        "com.sun.mc.softphone.sip.REGISTRAR_TRANSPORT");

                sipUri.setHost(registrarAddress);
                sipUri.setPort(registrarPort);

                if (registrarTransport == null) {
                    registrarTransport = "udp";
                }

                sipUri.setTransportParam(registrarTransport);

                System.out.println("Registrar workaround setting To Header: "
                        + "Host = " + registrarAddress + " Port = " + registrarPort
                        + " Transport " + registrarTransport);
            }

            add.setURI(sipUri);
            to.setAddress(add);
            reoriginatedRequest.setHeader(to);

        }

        //very ugly but very necessary
        sipManager.setCurrentlyUsedURI(sipUri.toString());
    }
}

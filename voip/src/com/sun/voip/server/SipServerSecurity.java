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
package com.sun.voip.server;

import com.sun.voip.Logger;
import com.sun.voip.sip.security.SecurityAuthority;
import com.sun.voip.sip.security.SipSecurityManager;
import com.sun.voip.sip.security.UserCredentials;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handle security for the Sip Server
 * @author Jonathan Kaplan <jonathankap@gmail.com>
 */
public class SipServerSecurity implements SecurityAuthority 
{
    private static final String REALM_FILE_PROP = "com.sun.voip.server.CredentialsFile";
    
    /** a map of credentials by realm */
    private final Map<String, UserCredentials> credentials =
            new HashMap<String, UserCredentials>();
    
    private final SipSecurityManager securityManager = 
            new SipSecurityManager();
    
    /**
     * Read initial realms file
     */
    public void initialize() {
        // setup the security manager
        securityManager.setSecurityAuthority(this);
        securityManager.setHeaderFactory(SipServer.getHeaderFactory());
        securityManager.setTransactionCreator(SipServer.getSipProvider());
        
        // try to read the realms file
        String realmFile = System.getProperty(REALM_FILE_PROP);
        if (realmFile != null) {
            try {
                Reader reader = new FileReader(new File(realmFile));
                parseCredentialsFile(reader);
            } catch (IOException ioe) {
                Logger.exception("Error reading " + realmFile, ioe);
            }
        } 
    }
    
    public SipSecurityManager getSipSecurityManager() {
        return securityManager;
    }
    
    public UserCredentials obtainCredentials(String realm, 
                                             UserCredentials defaultValues) 
    {
        UserCredentials known = getCredentials(realm);
        if (known != null) {
            // replace any non-null properties in the known set
            if (known.getAuthenticationUserName() != null) {
                defaultValues.setAuthenticationUserName(known.getAuthenticationUserName());
            }
            
            if (known.getUserName() != null) {
                defaultValues.setUserName(known.getUserName());
            }
            
            if (known.getPassword() != null) {
                defaultValues.setPassword(known.getPassword());
            }
        }
        
        return defaultValues;
    }    
    
    /**
     * Get the credential properties for the given realm
     * @param realm the realm to get properties for
     * @return the known credentials for that realm, or null if no
     * credentials are known for the given realm
     */
    public UserCredentials getCredentials(String realm) {
        return credentials.get(realm);
    }
    
    /**
     * Set the credential properties for the given realm.
     * @param realm the realm to get properties for
     * @param uc the credentials to set
     */
    public void setCredentials(String realm, UserCredentials uc) {
        credentials.put(realm, uc);
    }
    
    /**
     * Remove credentials for the given realm
     * @param realm the realm to remove credentials for
     */
    public void removeCredentials(String realm) {
        credentials.remove(realm);
    }
    
    /**
     * Remove all credentials for all realms
     */
    public void removeAllCredentials() {
        credentials.clear();
    }
    
    /**
     * Parse a credentials file of the form:
     * 
     * realm <name>
     * username <username>
     * authname <authenticationname>
     * password <password>
     * end
     */
    public void parseCredentialsFile(Reader content) throws IOException {
        // first, remove all existing credentials
        removeAllCredentials();
        
        BufferedReader br = new BufferedReader(content);
        int lineNumber = 0;
        String line = null;
        
        String curRealm = null;
        UserCredentials curUC = null;
        
        try {
            while ((line = br.readLine()) != null) {
                lineNumber++;
            
                line = line.trim();
                if (line.startsWith("#") || line.length() == 0) {
                    continue;
                }
                
                String[] parts = line.split("\\s+");
                if (parts[0].toLowerCase().equals("realm")) {
                    if (curRealm != null) {
                        throw new ParseException("Missing end", 0);
                    }
                    
                    if (parts.length != 2) {
                        throw new ParseException("Unable to read realm", 0);
                    }
                    
                    curRealm = parts[1];
                    curUC = new UserCredentials();
                } else if (parts[0].toLowerCase().equals("username")) {
                    if (curRealm == null) {
                        throw new ParseException("No current realm", 0);
                    }
                    
                    if (parts.length != 2) {
                        throw new ParseException("Unable to read username", 0);
                    }
                    
                    curUC.setUserName(parts[1]);
                } else if (parts[0].toLowerCase().equals("authname")) {
                    if (curRealm == null) {
                        throw new ParseException("No current realm", 0);
                    }
                    
                    if (parts.length != 2) {
                        throw new ParseException("Unable to read authname", 0);
                    }
                    
                    curUC.setAuthenticationUserName(parts[1]);
                } else if (parts[0].toLowerCase().equals("password")) {
                    if (curRealm == null) {
                        throw new ParseException("No current realm", 0);
                    }
                    
                    if (parts.length != 2) {
                        throw new ParseException("Unable to read password", 0);
                    }
                    
                    curUC.setPassword(parts[1].toCharArray());
                } else if (parts[0].toLowerCase().equals("end")) {
                    if (curRealm == null) {
                        throw new ParseException("No current realm", 0);
                    }
                    
                    setCredentials(curRealm, curUC);
                    curRealm = null;
                    curUC = null;
                } else {
                    throw new ParseException("Unrecognized line", 0);
                }
            }
        } catch (ParseException pe) {
            throw new IOException("Error at line " + lineNumber + ": " +
                                  pe.getMessage() + "\n" + line);
        }
    }
}

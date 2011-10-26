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
 */

package com.sun.voip.sip.security;

import com.sun.voip.Logger;
import java.text.ParseException;
import java.util.ListIterator;
import javax.sip.ClientTransaction;
import javax.sip.InvalidArgumentException;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.ViaHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * The class handles authentication challenges, caches user credentials and
 * takes care (through the SecurityAuthority interface) about retrieving
 * passwords.
 *
 * @author Emil Ivov <emcho@dev.java.net>
 * @version 1.0
 */

public class SipSecurityManager
{
    private static SipSecurityLogger logger = new DefaultSipSecurityLoggerImpl();
    
    private SecurityAuthority securityAuthority = null;
    private HeaderFactory     headerFactory = null;
    private SipProvider       transactionCreator = null;

    /**
     * Credentials cached so far.
     */
    CredentialsCache cachedCredentials = new CredentialsCache();



    public SipSecurityManager()
    {
    }

    /**
     * set the header factory to be used when creating authorization headers
     */
    public void setHeaderFactory(HeaderFactory headerFactory)
    {
        try{
            logger.logEntry();

            this.headerFactory = headerFactory;
        }
        finally
        {
            logger.logExit();
        }

    }

    /**
     * Verifies whether there are any user credentials registered for the call
     * that "request" belongs to and appends corresponding authorization headers
     * if that is the case.
     *
     * @param request the request that needs to be attached credentials.
     */
    public void appendCredentialsIfNecessary(Request request)
    {
        //TODO IMPLEMENT
    }

    /**
     * Uses securityAuthority to determinie a set of valid user credentials
     * for the specified Response (Challenge) and appends it to the challenged
     * request so that it could be retransmitted.
     *
     * @param challenge the 401/407 challenge response
     * @param challenged the request that caused the challenge and that is to be
     * retransmitted
     * @return the reoriginated request
     * @throws SipSecurityException
     */
    public ClientTransaction handleChallenge(Response challenge,
                                   String branchID,
                                   Request challengedRequest)
        throws SipSecurityException, SipException, InvalidArgumentException, ParseException
    {
       try{
            logger.logEntry();

            Request reoriginatedRequest = (Request)challengedRequest.clone();

            ListIterator authHeaders = null;

            if(challenge == null || reoriginatedRequest == null)
                throw new NullPointerException(
                    "A null argument was passed to handle challenge.");

//            CallIdHeader callId =
//                        (CallIdHeader)challenge.getHeader(CallIdHeader.NAME);

            if (challenge.getStatusCode() == Response.UNAUTHORIZED)
                authHeaders = challenge.getHeaders(WWWAuthenticateHeader.NAME);
            else if(challenge.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED)
                authHeaders = challenge.getHeaders(ProxyAuthenticateHeader.NAME);

            if(authHeaders == null)
                throw new SecurityException(
                    "Could not find WWWAuthenticate or ProxyAuthenticate headers");

            //Remove all authorization headers from the request (we'll re-add them
            //from cache)
            reoriginatedRequest.removeHeader(AuthorizationHeader.NAME);
            reoriginatedRequest.removeHeader(ProxyAuthorizationHeader.NAME);
            reoriginatedRequest.removeHeader(ViaHeader.NAME);
            
            ClientTransaction retryTran =
                transactionCreator.getNewClientTransaction(reoriginatedRequest);

            WWWAuthenticateHeader authHeader = null;
            while(authHeaders.hasNext())
            {
                authHeader = (WWWAuthenticateHeader)authHeaders.next();
                String realm = authHeader.getRealm();

                //Check whether we have cached credentials for authHeader's realm
                //make sure that if such credentials exist they get removed. The
                //challenge means that there's something wrong with them.
                CredentialsCacheEntry ccEntry =
                    (CredentialsCacheEntry)cachedCredentials.remove(realm);

                //Try to guess user name and facilitate user
                UserCredentials defaultCredentials = new UserCredentials();
                FromHeader from =
                    (FromHeader)reoriginatedRequest.getHeader(FromHeader.NAME);
                URI uri = from.getAddress().getURI();
                if (uri.isSipURI())
                {
                    String user =  ((SipURI) uri).getUser();
                    defaultCredentials.setUserName(user== null?"":user);
                }

                boolean ccEntryHasSeenTran = false;

                if(ccEntry !=null)
                    ccEntryHasSeenTran = ccEntry.processResponse(branchID);

				//get a new pass
                if(ccEntry == null // we don't have credentials for the relm
                   || ( (!authHeader.isStale() && ccEntryHasSeenTran))) // we have already tried with those and this is (!stale) not just a request to reencode
                {

                        logger.debug(
                            "We don't seem to have a good pass! Get one.");
                        if(ccEntry == null)
                        	ccEntry = new CredentialsCacheEntry();

                        ccEntry.userCredentials =
                            getSecurityAuthority().obtainCredentials(
                            realm,
                            defaultCredentials);
				}
                //encode and send what we have
                else if(ccEntry != null
                        &&( !ccEntryHasSeenTran || authHeader.isStale()))
                {
                    logger.debug(
                            "We seem to have a pass in the cache. Let's try with it.");
                }

                //if user canceled or sth else went wrong
                if(ccEntry.userCredentials == null)
                        throw new SecurityException(
                            "Unable to authenticate with realm " + realm);


                AuthorizationHeader authorization =
                    this.getAuthorization(
                            reoriginatedRequest.getMethod(),
                            reoriginatedRequest.getRequestURI().toString(),
                            reoriginatedRequest.getContent()==null?"":reoriginatedRequest.getContent().toString(),
                            authHeader,
                            ccEntry.userCredentials);


                ccEntry.processRequest(retryTran.getBranchId());
                cachedCredentials.cacheEntry(realm, ccEntry);

                reoriginatedRequest.addHeader(authorization);

                //if there was trouble with the user - make sure we fix it

                if(uri.isSipURI())
                {
		    SipURI sipUri = (SipURI)uri;

                    sipUri.setUser(ccEntry.userCredentials.getUserName());
                    Address add = from.getAddress();
                    add.setURI(sipUri);
                    from.setAddress(add);
                    reoriginatedRequest.setHeader(from);
                    
                    // let subclasses perform custom updates
                    updateReoriginatedRequest(reoriginatedRequest);
                }

                //if this is a register - fix to as well



            }

            CSeqHeader cSeq =
    			(CSeqHeader) reoriginatedRequest.getHeader( (CSeqHeader.NAME));
			cSeq.setSequenceNumber(cSeq.getSequenceNumber() + 1);

            return retryTran;
        }
        finally
        {
            logger.logExit();
        }
    }

    /**
     * Update reoriginated request. This method can be used by subclasses to
     * make changes to the reoriginated request.
     * @param reoriginatedRequest the request that has been reoriginated
     */
    protected void updateReoriginatedRequest(Request request) 
        throws SipSecurityException, SipException, InvalidArgumentException, ParseException
    {
        // default does nothing
    }
    
    /**
     * Sets the SecurityAuthority instance that should be queried for user
     * credentials.
     *
     * @param authority the SecurityAuthority instance that should be queried
     * for user credentials.
     */
    public void setSecurityAuthority(SecurityAuthority authority)
    {
        this.securityAuthority = authority;
    }

    /**
     * Returns the SecurityAuthority instance that SipSecurityManager uses to
     * obtain user credentials.
     *
     * @param authority the SecurityAuthority instance that SipSecurityManager
     * uses to obtain user credentials.
     */
    public SecurityAuthority getSecurityAuthority()
    {
        return this.securityAuthority;
    }

    /**
     * Generates an authorisation header in response to wwwAuthHeader.
     *
     * @param method method of the request being authenticated
     * @param uri digest-uri
     * @param wwwAuthHeader the challenge that we should respond to
     * @param userCredentials username and pass
     * @return an authorisation header in response to wwwAuthHeader.
     */
    private AuthorizationHeader getAuthorization(String method,
                                                 String uri,
                                                 String requestBody,
                                                 WWWAuthenticateHeader authHeader,
                                                 UserCredentials userCredentials)
        throws SecurityException
    {
        if (userCredentials.getPassword() == null) {
            throw new SecurityException("No password for " + authHeader.getRealm() +
                                        " domain " + authHeader.getDomain());
        }
        
        String response = null;
        try
        {
            response = MessageDigestAlgorithm.calculateResponse(
                            authHeader.getAlgorithm(),
                            userCredentials.getAuthenticationUserName(),
                            authHeader.getRealm(),
                            new String(userCredentials.getPassword()),
                            authHeader.getNonce(),
                            //TODO we should one day implement those two null-s
                            null,//nc-value
                            null,//cnonce
                            method,
                            uri,
                            requestBody,
                            authHeader.getQop());
        }catch(NullPointerException exc)
        {
            throw new SecurityException("The authenticate header was malformatted", exc);
        }

        AuthorizationHeader authorization = null;
        try {
            if (authHeader instanceof ProxyAuthenticateHeader) {
                authorization = headerFactory.createProxyAuthorizationHeader(
                    authHeader.getScheme());
            }
            else {
                authorization = headerFactory.createAuthorizationHeader(authHeader.getScheme());
            }

// John Melton - changed to use the authentication user name
            authorization.setUsername(userCredentials.getAuthenticationUserName());
            authorization.setRealm(authHeader.getRealm());
            authorization.setNonce(authHeader.getNonce());
            authorization.setParameter("uri",uri);
            authorization.setResponse(response);
            if( authHeader.getAlgorithm() != null)
                authorization.setAlgorithm(authHeader.getAlgorithm());
            if( authHeader.getOpaque() != null)
                authorization.setOpaque(authHeader.getOpaque());
            authorization.setResponse(response);
        }
        catch (ParseException ex) {
            throw new
                SecurityException("Failed to create an authorization header!");
        }


        return authorization;
    }

 	public void cacheCredentials(String realm, UserCredentials credentials)
    {
    	CredentialsCacheEntry ccEntry = new CredentialsCacheEntry();
        ccEntry.userCredentials = credentials;

        this.cachedCredentials.cacheEntry(realm, ccEntry);
    }

    /**
     * Sets a valid SipProvider that would enable the security manager to map
     * credentials to transactionsand thus understand when it is suitable
     * to use cached passwords and when it should go ask the user.
     * @param transactionCreator a valid SipProvder instance
     */
    public void setTransactionCreator(SipProvider transactionCreator)
    {
    	this.transactionCreator = transactionCreator;
    }

    public static void setSipSecurityLogger(SipSecurityLogger logger) {
        SipSecurityManager.logger = logger;
    }
    
    public interface SipSecurityLogger {
        public void logEntry();
        public void logExit();
        public void debug(String message);
    }
    
    static class DefaultSipSecurityLoggerImpl implements SipSecurityLogger {
        public void logEntry() {
        }

        public void logExit() {
        }

        public void debug(String message) {
            if (Logger.logLevel >= Logger.LOG_DEBUG) {
                Logger.println(message);
	    }
        }        
    }
}

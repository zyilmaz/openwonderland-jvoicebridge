/**
 * Open Wonderland
 *
 * Copyright (c) 2011 - 2012, Open Wonderland Foundation, All Rights Reserved
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

/**
 * Notification about members of a conference
 */
public interface ConferenceMemberListener {
    /**
     * Conference member added
     * @param member the member who was added
     */
    public void memberJoined(ConferenceMember member);
    
    /**
     * Conference member initialized (or reinitialized)
     * @param member the member who was initialized
     */
    public void memberInitialized(ConferenceMember member);
    
    /**
     * Conference member removed
     * @param member the member who was removed
     */
    public void memberLeft(ConferenceMember member);
}

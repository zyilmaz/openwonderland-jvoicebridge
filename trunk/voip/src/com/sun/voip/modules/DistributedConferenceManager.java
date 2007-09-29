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

package com.sun.voip.modules;

import com.sun.voip.client.BridgeConnector;

import com.sun.voip.ConferenceEvent;
import com.sun.voip.DistributedBridge;
import com.sun.voip.Logger;

import com.sun.voip.server.ConferenceManager;
import com.sun.voip.server.SipServer;

import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import java.util.ArrayList;
import java.util.Hashtable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

/**
 * Manage conferences which reside on more than one bridge.
 * 
 * There is 1 table in the database:
 *
 *	- BridgeInfo list
 *	  This is the list of bridges running conferences. 
 *	  Columns are <conferenceId> <bridgeAddress> <numberOfMembers>
 *
 * A BridgeInfo entry is created when a bridge starts a conference.
 * 
 * The new bridge joining the conference calls each of the bridges in the
 * BridgeInfo list.  The new bridge adds itself to the BridgeInfo list.
 *
 * When a bridge joins the conference on another bridge, a ConnectionInfo entry 
 * is created and added to the ConnectionTable.  
 */
public class DistributedConferenceManager implements DistributedBridge {
    private static DistributedConferenceManager distributedConferenceManager;

    private DBManager dbM;

    /*
     * connectionTable keys are conferenceId's and entrys are ArrayLists
     * of ConnectionInfo entries.
     */
    private Hashtable connectionTable = new Hashtable();

    private InetSocketAddress localAddress;	// local bridge address
    private String driver;
    private String connectionUrl;

    private BridgeConnector bridgeConnector;

    private DistributedConferenceManager(String driver, String connectionUrl) 
	    throws IOException {

	this.driver = driver;
	this.connectionUrl = connectionUrl;

	localAddress = SipServer.getSipAddress();

	dbM = new DBManager(localAddress, driver, connectionUrl);
    }

    /*
     * This is called by the ModuleLoader.  The argument is actually String[]
     * with arg 0 being the driver name "org.postresgl.Driver" and arg 1 being
     * the connection URL "jdbc:postgresql://proteus/postgres".
     */
    public static void initializeModule(Object o) throws IOException {
	Logger.println(
	    "DistributedConferenceManager:  initializeModule() called");

	if (distributedConferenceManager != null) {
	    return;
	}

	if (o instanceof String[] == false) {
	    Logger.println("Bad args, not String[]:  " + o);
	    throw new IOException("Bad args, not String[]:  " + o);
	}

	String[] args = (String[]) o;

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    for (int i = 0; i < args.length; i++) {
	        Logger.println("Args " + i + ": " + args[i]);
	    }
	}

	distributedConferenceManager = new DistributedConferenceManager(
	    args[0], args[1]);

	/*
	 * Tell the conferenceManager we're here so we will be notified of
	 * conference events.
	 */
	ConferenceManager.setDistributedBridge(distributedConferenceManager);
    }

    public synchronized void conferenceEventNotification(
	    ConferenceEvent event) {

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    log("conferenceEventNotification for conference " 
		+ event);
	}

	if (bridgeConnector == null) {
	    try {
	        bridgeConnector = new BridgeConnector(
	            localAddress.getAddress().getHostAddress(), 6666);
	    } catch (IOException e) {
		log("Unable to connect to bridge "
		    + localAddress.getAddress().getHostAddress());
		return;
	    }
	}

	try {
	    switch (event.getEvent()) {
	    case ConferenceEvent.CONFERENCE_STARTED:
	        conferenceStarted(event);
		break;
	
	    case ConferenceEvent.CONFERENCE_ENDED:
		conferenceEnded(event);
		break;

	    case ConferenceEvent.MEMBER_JOINED:
	        memberJoined(event);
                break;

	    case ConferenceEvent.MEMBER_LEFT:
		memberLeft(event);
	        break;

	    default:
		log("Unknown conferenceEvent");
		break;
	    }
	} catch (IOException e) {
	    log(e.getMessage());
	}
    }

    /*
     * Find other bridges running this conference and call them.
     */
    private void conferenceStarted(ConferenceEvent event) throws IOException {
	String conferenceId = event.getConferenceId();

	String[] tokens = conferenceId.split(":");

	if (tokens.length > 1) {
	    conferenceId = tokens[0];
	}

	Logger.println("Dist conf " + conferenceId 
	    + " full id: " + event.getConferenceId());

	/*
	 * Add local bridge to the list of bridges which 
	 * have joined this conference.  The returned list
	 * has bridges (excluding this one) which have already
	 * joined <conferenceId>.  We use this list to join
	 * the conference on the other bridges.
	 */
	ArrayList bridgeList = dbM.addBridge(conferenceId);

	addConference(conferenceId);
	
	for (int i = 0; i < bridgeList.size(); i++) {
	    String cmd = "c=" + conferenceId;

	    if (tokens.length >= 2) {
		cmd += ":" + tokens[1];
	    }

	    if (tokens.length >= 3) {
		cmd += ":" + tokens[2];
	    }

	    cmd += "\n";

	    cmd += "m=true\n";	// mute call
	    cmd += "mc=true\n";	// mute conference
	    cmd += "db=true\n";	// distributed bridge
	    cmd += "name=DistributedBridge" + "\n";

	    BridgeInfo bridgeInfo = (BridgeInfo) bridgeList.get(i);

	    cmd += "phoneNumber=sip:6666@"
		+ bridgeInfo.address.getAddress().getHostAddress() + ":" 
		+ bridgeInfo.address.getPort() + "\n";

	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        log("Sending " + cmd + " to " + bridgeInfo);
	    }

	    try {
	        bridgeConnector.sendCommand(cmd);
	    } catch (IOException e) {
		log("Unable to send cmd to bridge: " + cmd);
	    }
	}
    }

    private void conferenceEnded(ConferenceEvent event) {
	String conferenceId = event.getConferenceId();

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    log("Distributed conference ending:  " + conferenceId);
	}

	dbM.removeBridge(conferenceId);
	removeConference(conferenceId);
    }

    private void memberJoined(ConferenceEvent event) {
        if (Logger.logLevel >= Logger.LOG_INFO) {
	    log("callId " + event.getCallId() + " joining conference " 
		+ event.getConferenceId() + ", isDistributedBridge "
		+ event.isDistributedBridge());
	}

	if (event.isDistributedBridge() == false) {
	    dbM.updateNumberOfBridgeMembers(event.getConferenceId(), 1);
	    return;
	} 

        /*
         * TODO:  If there is no common mix, then there's no need
         * for a new connection.  Everything must be handled by private mixes.
         */
	if (ConferenceManager.hasCommonMix(event.getConferenceId())) {
	    addConnection(event.getConferenceId(), event.getMemberAddress(), 
	        event.getCallId());
	}

	setPrivateMixes(event.getConferenceId());
    }

    private void memberLeft(ConferenceEvent event) {
	String conferenceId = event.getConferenceId();
	String callId = event.getCallId();

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    log(callId + " leaving conference " + conferenceId);
	}

	if (event.isDistributedBridge() == false) {
	    dbM.updateNumberOfBridgeMembers(conferenceId, -1);
	    return;
	}

	removeConnection(conferenceId, event.getMemberAddress());
    }

    private void addConference(String conferenceId) {
	synchronized (connectionTable) {
	    connectionTable.put(conferenceId, new ArrayList());
	}
    }

    private void removeConference(String conferenceId) {
	synchronized (connectionTable) {
	    if (getConnectionList(conferenceId).size() > 0) {
		log("removeConference:  conferenceList is not empty!");
	    }

	    connectionTable.remove(conferenceId);
	}
    }

    /*
     * Since every bridge for a conference is connected to every other bridge,
     * we do not want to forward data received from one bridge to another.
     * We accomplish this by using private mixes setting the volume to 0 for 
     * calls between bridges.
     */
    private void setPrivateMixes(String conferenceId) {
	ArrayList connectionList = getConnectionList(conferenceId);

	for (int i = 0; i < connectionList.size(); i++) {
	    ConnectionInfo connection = (ConnectionInfo) connectionList.get(i);

	    for (int j = i + 1; j < connectionList.size(); j++) {
		ConnectionInfo c = (ConnectionInfo) connectionList.get(j);

		setPrivateMix(c.callId, connection.callId);
		setPrivateMix(connection.callId, c.callId);
	    }

	    unmute(connection.callId);
	}
    }

    private void setPrivateMix(String callId1, String callId2) {
	String cmd = "privateMix=0:" + callId1 + ":" + callId2;

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    log("Setting private mix:  " + cmd);
	}

	try {
	    bridgeConnector.sendCommand(cmd);
        } catch (IOException e) {
            log("Unable to send cmd to bridge " + cmd);
	}
    }

    private void unmute(String callId) {
	String cmd = "mute=false:" + callId + "\n"
	    + "muteConference=false:" + callId;

	if (Logger.logLevel >= Logger.LOG_INFO) {
	    log("Unmuting:  " + cmd);
	}

        try {
            bridgeConnector.sendCommand(cmd);
        } catch (IOException e) {
            log("Unable to send cmd to bridge " + cmd);
	}
    }

    public int getNumberOfMembers(String conferenceId) {
	return dbM.getNumberOfMembers(conferenceId);
    }

    public String getDistributedConferenceInfo() {
	dbM.dumpLog();

	String s = "";

	ArrayList bridgeList = dbM.getBridgeList();

	ArrayList conferenceList = getConferenceList(bridgeList);

	for (int i = 0; i < conferenceList.size(); i++) {
	    String conferenceId = (String) conferenceList.get(i);

	    s += getBridgeList(conferenceId, bridgeList);

	    s += "\n";

	    if (Logger.logLevel >= Logger.LOG_INFO) {
	        s += getConnections(conferenceId);

	        s += "\n";
	    }
	}

	return s;
    }

    private ArrayList getConferenceList(ArrayList bridgeList) {
	ArrayList conferenceList = new ArrayList();

	String lastConferenceId = "";

	for (int i = 0; i < bridgeList.size(); i++) {
	    BridgeInfo bridgeInfo = (BridgeInfo) bridgeList.get(i);

	    if (bridgeInfo.conferenceId.equals(lastConferenceId) == false) {
		conferenceList.add(bridgeInfo.conferenceId);
		lastConferenceId = bridgeInfo.conferenceId;
	    }
	}

	return conferenceList;
    }

    private String getBridgeList(String conferenceId, ArrayList bridgeList) {
	int numberOfMembers = 0;
	int numberOfBridges = 0;
	String bridges = "";

	for (int i = 0; i < bridgeList.size(); i++) {
	    BridgeInfo bridgeInfo = (BridgeInfo) bridgeList.get(i);

	    if (bridgeInfo.conferenceId.equals(conferenceId) == false) {
		continue;
	    }

	    numberOfMembers += bridgeInfo.numberOfMembers;
	    numberOfBridges++;

	    bridges += "    " + bridgeInfo + "\n";
	}

	String displayName = ConferenceManager.getDisplayName(conferenceId);

	if (displayName != null && displayName.length() > 0) {
	    displayName = " '" + displayName + "'";
	} else {
	    displayName = "";
	}
	
	return conferenceId + displayName
	    + " Members=" + numberOfMembers 
	    + " Bridges=" + numberOfBridges + "\n" + bridges;
    }

    private String getConnections(String conferenceId) {
	ArrayList connectionList = getConnectionList(conferenceId);

	if (connectionList == null) {
	    return "";
	}

	String s = "";

	for (int i = 0; i < connectionList.size(); i++) {
	    ConnectionInfo connection = (ConnectionInfo) connectionList.get(i);

	    s += "    " + connection + "\n";
	}

	return s;
    }

    public void dropDb() {
	try {
    	    connectionTable = new Hashtable();
	    dbM.dumpLog();
	    dbM.dropDb();
	    dbM = new DBManager(localAddress, driver, connectionUrl);
	} catch (IOException e) {
	    log("Unable to dropDb:  " + e.getMessage());
	}

    }

    class ConnectionInfo {
        public String callId;
        public InetSocketAddress remoteAddress;

        public ConnectionInfo(String callId, InetSocketAddress remoteAddress) {
	    this.callId = callId;
	    this.remoteAddress = remoteAddress;
        }

        public String toString() {
	    return callId + "::" 
	        + localAddress.getAddress().getHostName() 
	        + "(" + localAddress.getAddress().getHostAddress() + ")"
	        + ":" + localAddress.getPort() + " --> " 
	        + remoteAddress.getAddress().getHostName()
	        + "(" + remoteAddress.getAddress().getHostAddress() + ")"
	        + ":" + remoteAddress.getPort();
        }
    }

    private void addConnection(String conferenceId, 
	    InetSocketAddress remoteAddress, String callId) {

	synchronized (connectionTable) {
	    ArrayList connectionList = (ArrayList) 
		connectionTable.get(conferenceId);
	
	    connectionList.add(new ConnectionInfo(callId, remoteAddress));
	}

    }

    private void removeConnection(String conferenceId, 
	    InetSocketAddress remoteAddress) {

	synchronized (connectionTable) {
	    ArrayList connectionList = (ArrayList) 
		connectionTable.get(conferenceId);

	    for (int i = 0; i < connectionList.size(); i++) {
		ConnectionInfo connection = (ConnectionInfo) 
		    connectionList.get(i);

		if (connection.remoteAddress.equals(remoteAddress)) {
		    connectionList.remove(connection);
		    return;
		}
	    }
	}
    }

    public ArrayList getConnectionList(String conferenceId) {
	return (ArrayList) connectionTable.get(conferenceId);
    }

    public void log(String s) {
	Logger.println(s);

	dbM.log(s);
    }

}

class BridgeInfo {
    public String conferenceId;
    public InetSocketAddress address;
    public int numberOfMembers;

    public BridgeInfo(String conferenceId, String host, int port, 
	    int numberOfMembers) {

	this.conferenceId = conferenceId;
	this.address = new InetSocketAddress(host, port);
	this.numberOfMembers = numberOfMembers;
    }

    public String toString() {
	return address.getAddress().getHostName()
	    + "(" + address.getAddress().getHostAddress() + ")"
	    + ":" + address.getPort() + ", Members=" + numberOfMembers;
    }

}

/***** Database specific code *****/

class DBManager {

    InetSocketAddress localAddress;
    private String connectionUrl;

    private Connection db;
    private Statement st;

    public DBManager(InetSocketAddress localAddress, String driver, 
	    String connectionUrl) throws IOException {

	this.localAddress = localAddress;
	this.connectionUrl = connectionUrl;

	synchronized (this) {
	    try {
	        Class.forName(driver);	  // load the driver
                Logger.println("Connecting to Database URL = " 
		    + connectionUrl);

                db = DriverManager.getConnection(connectionUrl, "jp", null); 
                st = db.createStatement();

		try {
                    st.executeUpdate("create table BridgeList("
                        + "conferenceId varchar, "
                        + "host varchar, "
                        + "port int4, "
			+ "numberOfMembers int4, "
			+ "constraint pk_bridgeList primary key "
			+ "(conferenceId, host, port))");

		    Logger.println("Created BridgeList table");
                } catch (SQLException e) {
                    if (e.getMessage().indexOf("exists") >= 0) {
		        Logger.println(
			    "DistributedConferenceManager using exising "
			    + "BridgeList table");
		    } else {
	        	e.printStackTrace();
			throw new IOException(e.getMessage());
                    } 
		}

	        /*
	         * remove any leftover entries for this host
	         */
                st.executeUpdate("DELETE FROM BridgeList WHERE host='"
                    + localAddress.getAddress().getHostAddress() + "'"
		    + " AND port=" + localAddress.getPort());

		try {
                    st.executeUpdate("create table BridgeLog("
                        + "host varchar, message varchar)");

                    Logger.println("Created BridgeLog table");
                } catch (SQLException e) {
                    if (e.getMessage().indexOf("exists") >= 0) {
                        Logger.println(
                            "DistributedConferenceManager using exising "
                            + "BridgeLog table");
                    } else {
                        e.printStackTrace();
                        throw new IOException(e.getMessage());
                    }
                }
	    } catch (Exception e) {
	        e.printStackTrace();
	        throw new IOException(e.getMessage());
	    }
	}
    }

    private void reconnect() {
	try {
            db = DriverManager.getConnection(connectionUrl, "jp", null); 
            st = db.createStatement();
	} catch (SQLException e) {
	    Logger.println("Unable to reconnect:  " + e.getMessage());
	}
    }

    public void dropDb() throws IOException {
	try {
	    st.executeUpdate("drop table BridgeList");
            Logger.println("BridgeList removed");

	    st.executeUpdate("drop table BridgeLog");
	    Logger.println("BridgeLog removed");

	    st.close();
	    db.close();
	} catch (SQLException e) {
            Logger.println("dropDb:  " + e.getMessage());
	    throw new IOException("dropDb:  " + e.getMessage());
        }
    }

    public int getNumberOfMembers(String conferenceId) {
	int numberOfMembers = 0;

	try {
            ResultSet rs = st.executeQuery("SELECT * FROM BridgeList WHERE "
		+ "conferenceId='" + conferenceId + "'");

            if (rs != null) {
                while (rs.next()) {
                    numberOfMembers += rs.getInt("numberOfMembers");
                }

                rs.close();
            }
        } catch (SQLException e) {
            Logger.println("getNumberOfMembers:  " + e.getMessage());
	    reconnect();
        }

	return numberOfMembers;
    }

    public ArrayList getBridgeList() {
        ArrayList bridgeList = new ArrayList();

        try {
            ResultSet rs = st.executeQuery("SELECT * FROM BridgeList "
		+ "ORDER BY conferenceId, host, port");

            if (rs != null) {
                while (rs.next()) {
		    String conferenceId = rs.getString("conferenceId");
		    String host = rs.getString("host");
		    int port = rs.getInt("port");
		    int numberOfMembers = rs.getInt("numberOfMembers");

                    bridgeList.add(new BridgeInfo(conferenceId, host, port, 
			numberOfMembers));
                }

                rs.close();
            }
        } catch (SQLException e) {
            Logger.println("getBridgeList:  " + e.getMessage());
	    reconnect();
        }

        return bridgeList;
    }

    /*
     * Add this bridge to the list of bridges running this conference.
     */
    public ArrayList addBridge(String conferenceId) {
	ArrayList bridgeList = new ArrayList();

	synchronized (this) {
            try {
                st.executeUpdate("BEGIN");
                db.setAutoCommit(false);

                ResultSet rs = 
		    st.executeQuery("SELECT * FROM BridgeList");

                if (rs != null) {
                    while (rs.next()) {
			BridgeInfo bridgeInfo = new BridgeInfo(
			    rs.getString("conferenceId"), 
			    rs.getString("host"),
			    rs.getInt("port"),
		    	    rs.getInt("numberOfMembers"));

			if (bridgeInfo.conferenceId.equals(conferenceId)) {
			    if (Logger.logLevel >= Logger.LOG_INFO) {
			        Logger.println("Found bridge " + bridgeInfo);
			    }
		            bridgeList.add(bridgeInfo);
			}
                    }
		    rs.close();
                }

                st.executeUpdate("INSERT INTO BridgeList values(" 
		    + "'"   + conferenceId + "'"
		    + ", '" + localAddress.getAddress().getHostAddress() + "'"
		    + ", "  + localAddress.getPort() + ", 0)");

                db.commit();
                db.setAutoCommit(true);
            } catch (SQLException e) {
                Logger.println("addBridge for " + localAddress + ":  " 
		    + e.getMessage());
	        reconnect();
            }

	    try {
	        st.executeUpdate("END");
            } catch (SQLException e) {
                Logger.println("addBridge for " + localAddress + ":  " 
		    + e.getMessage());
	        reconnect();
            }

	    return bridgeList;
	}
    }

    public void removeBridge(String conferenceId) {
	synchronized (this) {
	    try {
	        st.executeUpdate("DELETE FROM BridgeList WHERE "
		    + "conferenceId='" + conferenceId + "' AND "
		    + "host ='" +  localAddress.getAddress().getHostAddress() 
		    + "'" + " AND port=" + localAddress.getPort());

	    } catch (SQLException e) {
                Logger.println("removeBridge for " + localAddress + ":  "
                    + e.getMessage());
	        reconnect();
            }
	}
    }

    public void updateNumberOfBridgeMembers(String conferenceId, int n) {
        synchronized (this) {
	    for (int i = 0; i < 2; i++) {
                try {
                    st.executeUpdate(
                        "update BridgeList set numberOfMembers="
                        + "numberOfMembers+" + n
                        + " WHERE conferenceId='" + conferenceId + "' AND "
 		        + "host='" + localAddress.getAddress().getHostAddress() 
		        + "'" + " AND port=" + localAddress.getPort());
		    break;
                } catch (SQLException e) {
                    Logger.println("updateNumberOfBridgeMembers for "
                        + localAddress + " " + conferenceId + ":  "
                        + e.getMessage());

		    reconnect();
		}
            }
        }
    }

    public void log(String s) {
        synchronized (this) {
	    try {
                st.executeUpdate("INSERT INTO BridgeLog values("
                    + "'" + Logger.getDate() + "--- "
		    + localAddress.getAddress().getHostName()
		    + "(" + localAddress.getAddress().getHostAddress() + ")'"
                    + ", '"  + s + "')");
            } catch (SQLException e) {
                Logger.println("log for " + localAddress + ":  " 
		    + e.getMessage());
		reconnect();
            }
	}
    }

    public void dumpLog() {
	Logger.println("--- DUMPING DISTRIBUTED BRIDGE LOG MESSAGES ---");

	ArrayList messages = new ArrayList();

	synchronized (this) {
            try {
                st.executeUpdate("BEGIN");

                ResultSet rs =
                    st.executeQuery("SELECT * FROM BridgeLog");

                if (rs != null) {
                    while (rs.next()) {
                        messages.add(new String(
			    rs.getString("host") + ": " 
			    + rs.getString("message")));
                    }
                    rs.close();
                }

                st.executeUpdate("DELETE FROM BridgeLog");
	    } catch (SQLException e) {
		Logger.println("dumpLog:  " + e.getMessage());
		reconnect();
	    }

            try {
                st.executeUpdate("END");
            } catch (SQLException e) {
                Logger.println("dumpLog:  " + e.getMessage());
		reconnect();
	    }
	}

	for (int i = 0; i < messages.size(); i++) {
	    System.out.println((String) messages.get(i));
	}

	Logger.println(
	    "--- FINISHED DUMPING DISTRIBUTED BRIDGE LOG MESSAGES ---");
    }

}

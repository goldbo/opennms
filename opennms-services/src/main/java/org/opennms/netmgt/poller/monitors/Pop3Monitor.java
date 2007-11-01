//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2002-2003 The OpenNMS Group, Inc. All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Modifications:
//
// 2004 May 05: Switch from SocketChannel to Socket with connection timeout.
// 2003 Jul 21: Explicitly closed socket.
// 2003 Jul 18: Enabled retries for monitors.
// 2003 Jun 11: Added a "catch" for RRD update errors. Bug #748.
// 2003 Jan 31: Added the ability to imbed RRA information in poller packages.
// 2003 Jan 31: Cleaned up some unused imports.
// 2003 Jan 29: Added response times to certain monitors.
// 2002 Nov 14: Used non-blocking I/O socket channel classes.
//
// Original code base Copyright (C) 1999-2001 Oculan Corp. All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
// For more information contact:
//      OpenNMS Licensing <license@opennms.org>
//      http://www.opennms.org/
//      http://www.opennms.com/
//
// Tab Size = 8
//

package org.opennms.netmgt.poller.monitors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.log4j.Level;
import org.opennms.netmgt.model.PollStatus;
import org.opennms.netmgt.poller.Distributable;
import org.opennms.netmgt.poller.MonitoredService;
import org.opennms.netmgt.poller.NetworkInterface;
import org.opennms.netmgt.poller.NetworkInterfaceNotSupportedException;
import org.opennms.netmgt.utils.ParameterMap;

/**
 * <P>
 * This class is designed to be used by the service poller framework to test the
 * availability of the POP3 service on remote interfaces. The class implements
 * the ServiceMonitor interface that allows it to be used along with other
 * plug-ins by the service poller framework.
 * </P>
 * 
 * @author <A HREF="mailto:tarus@opennms.org">Tarus Balog </A>
 * @author <A HREF="mailto:mike@opennms.org">Mike </A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS </A>
 * 
 */

@Distributable
final public class Pop3Monitor extends IPv4Monitor {

    /**
     * Default POP3 port.
     */
    private static final int DEFAULT_PORT = 110;

    /**
     * Default retries.
     */
    private static final int DEFAULT_RETRY = 0;

    /**
     * Default timeout. Specifies how long (in milliseconds) to block waiting
     * for data from the monitored interface.
     */
    private static final int DEFAULT_TIMEOUT = 3000;

    /**
     * <P>
     * Poll the specified address for POP3 service availability.
     * </P>
     * 
     * <P>
     * During the poll an attempt is made to connect on the specified port (by
     * default TCP port 110). If the connection request is successful, the
     * banner line generated by the interface is parsed and if the response
     * indicates that we are talking to an POP3 server we continue. Next, a POP3
     * 'QUIT' command is sent to the interface. Again the response is parsed and
     * verified. Provided that the interface's response is valid we set the
     * service status to SERVICE_AVAILABLE and return.
     * </P>
     * @param parameters
     *            The package parameters (timeout, retry, etc...) to be used for
     *            this poll.
     * @param iface
     *            The network interface to test the service on.
     * @return The availibility of the interface and if a transition event
     *         should be supressed.
     * 
     */
    public PollStatus poll(MonitoredService svc, Map parameters) {
        NetworkInterface iface = svc.getNetInterface();

        // Get interface address from NetworkInterface
        //
        if (iface.getType() != NetworkInterface.TYPE_IPV4)
            throw new NetworkInterfaceNotSupportedException("Unsupported interface type, only TYPE_IPV4 currently supported");

        // Process parameters
        //
        TimeoutTracker tracker = new TimeoutTracker(parameters, DEFAULT_RETRY, DEFAULT_TIMEOUT);

        int port = ParameterMap.getKeyedInteger(parameters, "port", DEFAULT_PORT);

        InetAddress ipv4Addr = (InetAddress) iface.getAddress();

        if (log().isDebugEnabled())
            log().debug("poll: address = " + ipv4Addr + ", port = " + port + ", " + tracker);

        PollStatus serviceStatus = PollStatus.unavailable();

        for (tracker.reset(); tracker.shouldRetry() && !serviceStatus.isAvailable(); tracker.nextAttempt()) {
            Socket socket = null;
            try {
                //
                // create a connected socket
                //
                tracker.startAttempt();

                socket = new Socket();
                socket.connect(new InetSocketAddress(ipv4Addr, port), tracker.getConnectionTimeout());
                socket.setSoTimeout(tracker.getSoTimeout());
                log().debug("Pop3Monitor: connected to host: " + ipv4Addr + " on port: " + port);

                // We're connected, so upgrade status to unresponsive
                serviceStatus = PollStatus.unresponsive();
                BufferedReader rdr = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                //
                // Tokenize the Banner Line, and check the first
                // line for a valid return.
                //
                // Server response should start with: "+OK"
                //
                String banner = rdr.readLine();
                double responseTime = tracker.elapsedTimeInMillis();

                if (banner == null)
                    continue;
                StringTokenizer t = new StringTokenizer(banner);

                if (t.nextToken().equals("+OK")) {
                    //
                    // POP3 server should recoginize the QUIT command
                    //
                    String cmd = "QUIT\r\n";
                    socket.getOutputStream().write(cmd.getBytes());

                    //
                    // Parse the response to the QUIT command
                    //
                    // Server response should start with: "+OK"
                    //
                    t = new StringTokenizer(rdr.readLine());
                    if (t.nextToken().equals("+OK")) {
                        serviceStatus = PollStatus.available(responseTime);
                    }
                }

                // If we get this far and the status has not been set
                // to available, then something didn't verify during
                // the banner checking or QUIT command process.
                if (!serviceStatus.isAvailable()) {
                    serviceStatus = PollStatus.unavailable();
                }
            } catch (NoRouteToHostException e) {
            	
            	serviceStatus = logDown(Level.WARN, "No route to host exception for address " + ipv4Addr.getHostAddress(), e);
                
            } catch (InterruptedIOException e) {
            	
            	serviceStatus = logDown(Level.DEBUG, "did not connect to host with " + tracker);
            	
            } catch (ConnectException e) {
            	
            	serviceStatus = logDown(Level.DEBUG, "Connection exception for address " + ipv4Addr.getHostAddress(), e);
            } catch (IOException e) {
            	
            	serviceStatus = logDown(Level.DEBUG, "IOException while polling address " + ipv4Addr.getHostAddress(), e);
            } finally {
                try {
                    // Close the socket
                    if (socket != null)
                        socket.close();

                } catch (IOException e) {
                    if (log().isDebugEnabled())
                        log().debug("poll: Error closing socket.", e);
                }
            }
        }

        //
        // return the status of the service
        //
        return serviceStatus;
    }

}

/*----------------------------------------------------------------------------*
 *  Copyright (c) 2001        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    Author:  Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12H        *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-5800             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.et.apps;

import java.lang.*;

import org.jlab.coda.et.*;
import org.jlab.coda.et.system.*;

/**
 * This class is an example of starting up an ET system.
 *
 * @author Carl Timmer
 * @version 7.0
 */
public class StartEt {

    /** Method to print out correct program command line usage. */
    private static void usage() {
        System.out.println("\nUsage:\n" +
                "   java StartEt [-n <# of events>]\n" +
                "                [-s <size of events (bytes)>]\n" +
                "                [-p <server port>]\n" +
                "                [-u <udp port>]\n" +
                "                [-m <multicast port>]\n" +
                "                [-rb <TCP receive buffer size (bytes)>]\n" +
                "                [-sb <TCP send buffer size (bytes)>]\n" +
                "                [-nd] (turn on TCP no-delay)\n" +
                "                [-debug]\n" +
                "                [-h]\n" +
                "                -f <file name>\n");
    }


    public StartEt() {
    }

    public static void main(String[] args) {
        int numEvents = 3000, size = 128;
        int serverPort = EtConstants.serverPort;
        int udpPort = EtConstants.broadcastPort;
        int multicastPort = EtConstants.multicastPort;
        int recvBufSize=0, sendBufSize=0;
        boolean noDelay = false;
        boolean debug = false;
        String file=null;

        // loop over all args
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-h")) {
                usage();
                System.exit(-1);
            }
            else if (args[i].equalsIgnoreCase("-n")) {
                numEvents = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-f")) {
                file = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-p")) {
                serverPort = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-u")) {
                udpPort = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-m")) {
                multicastPort = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-s")) {
                size = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-rb")) {
                recvBufSize = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-sb")) {
                sendBufSize = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-nd")) {
                noDelay = true;
            }
            else if (args[i].equalsIgnoreCase("-debug")) {
                debug = true;
            }
            else {
                usage();
                System.exit(-1);
            }
        }

        if (file == null) {
            usage();
            System.exit(-1);
        }
        
        try {
            System.out.println("STARTING ET SYSTEM");
            // ET system configuration object
            SystemConfig config = new SystemConfig();

            //int[] groups = {30,30,40};
            //config.setGroups(groups);

            // listen for multicasts at this address
            config.addMulticastAddr(EtConstants.multicastAddr);
            // set tcp server port
            config.setServerPort(serverPort);
            // set port for listening for udp packets
            config.setUdpPort(udpPort);
            // set port for listening for multicast udp packets
            // (on Java this must be different than the udp port)
            config.setMulticastPort(multicastPort);
            // set total number of events
            config.setNumEvents(numEvents);
            // set size of events in bytes
            config.setEventSize(size);
            // set tcp receive buffer size in bytes
            if (recvBufSize > 0) {
                config.setTcpRecvBufSize(recvBufSize);
            }
            // set tcp send buffer size in bytes
            if (sendBufSize > 0) {
                config.setTcpSendBufSize(sendBufSize);
            }
            // set tcp no-delay
            if (noDelay) {
                config.setNoDelay(noDelay);
            }
            // set debug level
            if (debug) {
                config.setDebug(EtConstants.debugInfo);
            }
            // create an active ET system
            SystemCreate sys = new SystemCreate(file, config);
        }
        catch (Exception ex) {
            System.out.println("ERROR STARTING ET SYSTEM");
            ex.printStackTrace();
        }

    }
}

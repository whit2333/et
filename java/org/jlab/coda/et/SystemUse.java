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

package org.jlab.coda.et;

import java.lang.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.MappedByteBuffer;
import java.nio.ByteBuffer;

import org.jlab.coda.et.data.*;
import org.jlab.coda.et.exception.*;


/**
 * This class implements an object which allows a user to interact with an ET
 * system. It is not the ET system itself, but rather a proxy which communicates
 * over the network or through JNI with the real ET system.
 *
 * @author Carl Timmer
 */

public class SystemUse {

    /** Object to specify how to open the ET system of interest. */
    private SystemOpenConfig openConfig;

    /** Object used to connect to a real ET system. */
    private SystemOpen sys;

    /** Flag telling whether the real ET system is currently opened or not. */
    private boolean open;

    /** Debug level. */
    private int debug;

    /** Tcp socket connected to ET system's server. */
    private Socket sock;

    /** Flag specifying whether the ET system process is Java based or not. */
    private boolean isJava;

    /** Data input stream built on top of the socket's input stream (with an
     *  intervening buffered input stream). */
    private DataInputStream in;

    /** Data output stream built on top of the socket's output stream (with an
     *  intervening buffered output stream). */
    private DataOutputStream out;



    /**
     * Construct a new SystemUse object.
     *
     * @param config SystemOpenConfig object to specify how to open the ET
     *               system of interest (copy is stored & used)
     * @param debug  debug level (e.g. {@link Constants#debugInfo})
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception java.net.UnknownHostException
     *     if the host address(es) is(are) unknown
     * @exception EtException
     *     if the responding ET system has the wrong name, runs a different
     *     version of ET, or has a different value for
     *     {@link Constants#stationSelectInts}
     * @exception EtTooManyException
     *     if there were more than one valid response when policy is set to
     *     {@link Constants#policyError} and we are looking either
     *     remotely or anywhere for the ET system.
     */
    public SystemUse(SystemOpenConfig config, int debug) throws
            IOException, EtException, EtTooManyException {

        openConfig = new SystemOpenConfig(config);
        sys = new SystemOpen(openConfig);

        if ((debug != Constants.debugNone)   &&
            (debug != Constants.debugSevere) &&
            (debug != Constants.debugError)  &&
            (debug != Constants.debugWarn)   &&
            (debug != Constants.debugInfo))    {

            this.debug = Constants.debugError;
        }
        else {
            this.debug = debug;
        }

        sys.setDebug(debug);

        open();
    }

    /**
     * Construct a new SystemUse object. Debug level set to print only errors.
     *
     * @param config SystemOpenConfig object to specify how to open the ET
     *               system of interest (copy is stored & used)
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception java.net.UnknownHostException
     *     if the host address(es) is(are) unknown
     * @exception EtException
     *     if the responding ET system has the wrong name, runs a different
     *     version of ET, or has a different value for
     *     {@link Constants#stationSelectInts}
     * @exception EtTooManyException
     *     if there were more than one valid response when policy is set to
     *     {@link Constants#policyError} and we are looking either
     *     remotely or anywhere for the ET system.
     */
    public SystemUse(SystemOpenConfig config) throws
            IOException, EtException, EtTooManyException {

        openConfig = new SystemOpenConfig(config);
        sys        = new SystemOpen(openConfig);
        debug      = Constants.debugError;
        sys.setDebug(debug);
        open();
    }

    /**
     * Construct a new SystemUse object.
     *
     * @param sys   SystemOpen object to specify a connection to the ET
     *              system of interest
     * @param debug debug level (e.g. {@link Constants#debugInfo})
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception java.net.UnknownHostException
     *     if the host address(es) is(are) unknown
     * @exception EtException
     *     if the responding ET system has the wrong name, runs a different
     *     version of ET, or has a different value for
     *     {@link Constants#stationSelectInts}
     * @exception EtTooManyException
     *     if there were more than one valid response when policy is set to
     *     {@link Constants#policyError} and we are looking either
     *     remotely or anywhere for the ET system.
     */
    public SystemUse(SystemOpen sys, int debug)  throws
            IOException, EtException, EtTooManyException {

        this.sys   = sys;
        openConfig = sys.getConfig();

        if ((debug != Constants.debugNone)   &&
            (debug != Constants.debugSevere) &&
            (debug != Constants.debugError)  &&
            (debug != Constants.debugWarn)   &&
            (debug != Constants.debugInfo))    {

            this.debug = Constants.debugError;
        }
        else {
            this.debug = debug;
        }

        if (sys.isConnected()) {
            if (sys.getLanguage() == Constants.langJava) {isJava = true;}

            // buffer communication streams for efficiency
            sock = sys.getSocket();
            in   = new DataInputStream( new BufferedInputStream( sock.getInputStream(),  65535));
            out  = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream(), 65535));
            open = true;
        }
        else {
            open();
        }

    }


    // Local getters & setters

    
    /**
     * Get the data input stream to talk to ET system server.
     * @return data input stream to talk to ET system server
     */
    public DataInputStream getInputStream() {
        return in;
    }

    /**
     * Get the data output stream to receive from the ET system server.
     * @return data output stream to receive from the ET system server
     */
    public DataOutputStream getOutputStream() {
        return out;
    }

    /**
     * Gets the debug output level.
     * @return debug output level
     */
    public int getDebug() {
        return debug;
    }

    /**
     * Sets the debug output level. Must be either {@link Constants#debugNone},
     * {@link Constants#debugSevere}, {@link Constants#debugError},
     * {@link Constants#debugWarn}, or {@link Constants#debugInfo}.
     *
     * @param val debug level
     * @throws EtException if bad argument value
     */
    public void setDebug(int val) throws EtException {
        if ((val != Constants.debugNone)   &&
            (val != Constants.debugSevere) &&
            (val != Constants.debugError)  &&
            (val != Constants.debugWarn)   &&
            (val != Constants.debugInfo)) {
            throw new EtException("bad debug argument");
        }
        debug = val;
    }

    /**
     * Gets a copy of the configuration used to specify how to open the ET system.
     * @return SystemOpenConfig object used to specify how to open the ET system.
     */
    public SystemOpenConfig getConfig() {
        return new SystemOpenConfig(openConfig);
    }


    /**
     * Open the ET system and set up buffered communication.
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception java.net.UnknownHostException
     *     if the host address(es) is(are) unknown
     * @exception EtException
     *     if the responding ET system has the wrong name, runs a different
     *     version of ET, or has a different value for
     *     {@link Constants#stationSelectInts}
     * @exception EtTooManyException
     *     if there were more than one valid response when policy is set to
     *     {@link Constants#policyError} and we are looking either
     *     remotely or anywhere for the ET system.
     */
    synchronized private void open() throws IOException, EtException, EtTooManyException {
        try {
            sys.connect();
        }
        catch (EtTooManyException ex) {
            if (debug >= Constants.debugError) {
                System.out.println("The following hosts responded:");
                for (Map.Entry<String,Integer> entry : sys.getResponders().entrySet()) {
                    System.out.println("  " + entry.getKey() + " at port " + entry.getValue());
                }
            }
            throw ex;
        }

        if (sys.getLanguage() == Constants.langJava) {isJava = true;}

        sock = sys.getSocket();

        // buffer communication streams for efficiency
        in   = new DataInputStream( new BufferedInputStream( sock.getInputStream(),  65535));
        out  = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream(), 65535));
        open = true;
    }


    /** Close the ET system. ForcedClose and Close do the same thing in Java. */
    synchronized public void close() {
        // if communication with ET system fails, we've already been "closed"
        try {
            out.writeInt(Constants.netClose);
            out.flush();
            in.readInt();
        }
        catch (IOException ex) {
            if (debug >= Constants.debugError) {
                System.out.println("network communication error");
            }
        }
        finally {
            try {
                in.close();
                out.close();
                sock.close();
            }
            catch (IOException ex) { /* ignore exception */ }
        }

        open = false;
    }


    /**
     * Is the ET system alive - still up and running?
     *
     *  @return <code>true</code> if the ET system is alive, otherwise  <code>false</code>
     */
    synchronized public boolean alive() {
        int alive;
        // If ET system is NOT alive, or if ET system was killed and restarted
        // (breaking tcp connection), we'll get a read or write error.
        try {
            out.writeInt(Constants.netAlive);
            out.flush();
            alive = in.readInt();
        }
        catch (IOException ex) {
            if (debug >= Constants.debugError) {
                System.out.println("network communication error");
            }
            return false;
        }

        return (alive == 1);
    }


    /**
     * Wake up an attachment that is waiting to read events from a station's
     * empty input list.
     *
     * @param att attachment to wake up
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if the attachment object is invalid
     */
    synchronized public void wakeUpAttachment(Attachment att) throws IOException, EtException {
        if (!att.usable || att.sys != this) {
            throw new EtException("Invalid attachment");
        }

        out.writeInt(Constants.netWakeAtt);
        out.writeInt(att.id);
        out.flush();
    }


    /**
     * Wake up all attachments waiting to read events from a station's
     * empty input list.
     *
     * @param station station whose attachments are to wake up
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if the station object is invalid
     */
    synchronized public void wakeUpAll(Station station) throws IOException, EtException {
        if (!station.usable || station.sys != this) {
            throw new EtException("Invalid station");
        }

        out.writeInt(Constants.netWakeAll);
        out.writeInt(station.id);
        out.flush();
    }


    //****************************************************
    //                      STATIONS                     *
    //****************************************************

    
    /**
     * Checks a station configuration for self-consistency.
     *
     * @param config station configuration
     *
     * @exception EtException
     *     if the station configuration is not self-consistent
     */
    private void configCheck(StationConfig config) throws EtException {

        // USER mode means specifing a class
        if ((config.getSelectMode()  == Constants.stationSelectUser) &&
            (config.getSelectClass() == null)) {

            throw new EtException("station config needs a select class name");
        }

        // Must be parallel, block, not prescale, and not restore to input list if rrobin or equal cue
        if (((config.getSelectMode()  == Constants.stationSelectRRobin) ||
             (config.getSelectMode()  == Constants.stationSelectEqualCue)) &&
            ((config.getFlowMode()    == Constants.stationSerial) ||
             (config.getBlockMode()   == Constants.stationNonBlocking) ||
             (config.getRestoreMode() == Constants.stationRestoreIn)  ||
             (config.getPrescale()    != 1))) {

            throw new EtException("if flowMode = rrobin/equalcue, station must be parallel, nonBlocking, prescale=1, & not restoreIn");
        }

        // If redistributing restored events, must be a parallel station
        if ((config.getRestoreMode() == Constants.stationRestoreRedist) &&
            (config.getFlowMode()    != Constants.stationParallel)) {

            throw new EtException("if restoreMode = restoreRedist, station must be parallel");
        }

        if (config.getCue() > sys.getNumEvents()) {
            config.setCue(sys.getNumEvents());
        }
    }


    /**
     * Creates a new station placed at the end of the ordered list of stations.
     * If the station is added to a group of parallel stations,
     * it is placed at the end of the list of parallel stations.
     *
     * @param config  station configuration
     * @param name    station name
     *
     * @return new station object
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if the select method's class cannot be loaded, the position is less
     *     than 1 (GRAND_CENTRAL's spot), the name is GRAND_CENTRAL (already
     *     taken), the configuration's cue size is too big, or the configuration
     *     needs a select class name
     * @exception EtExistsException
     *     if the station already exists but with a different configuration
     * @exception EtTooManyException
     *     if the maximum number of stations has been created already
     */
    public Station createStation(StationConfig config, String name)
            throws IOException, EtException,
                   EtExistsException, EtTooManyException {

        return createStation(config, name, Constants.end, Constants.end);
    }


    /**
     * Creates a new station at a specified position in the ordered list of
     * stations. If the station is added to a group of parallel stations,
     * it is placed at the end of the list of parallel stations.
     *
     * @param config   station configuration
     * @param name     station name
     * @param position position in the linked list to put the station.
     *
     * @return new station object
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if the select method's class cannot be loaded, the position is less
     *     than 1 (GRAND_CENTRAL's spot), the name is GRAND_CENTRAL (already
     *     taken), the configuration's cue size is too big, or the configuration
     *     needs a select class name
     * @exception EtExistsException
     *     if the station already exists but with a different configuration
     * @exception EtTooManyException
     *     if the maximum number of stations has been created already
     */
    public Station createStation(StationConfig config, String name, int position)
            throws IOException, EtException,
            EtExistsException, EtTooManyException {
        return createStation(config, name, position, Constants.end);
    }


    /**
     * Creates a new station at a specified position in the ordered list of
     * stations and in a specified position in an ordered list of parallel
     * stations if it is a parallel station.
     *
     * @param config     station configuration
     * @param name       station name
     * @param position   position in the main list to put the station.
     * @param parallelPosition   position in the list of parallel
     *                           stations to put the station.
     *
     * @return new station object
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if the select method's class cannot be loaded, the position is less
     *     than 1 (GRAND_CENTRAL's spot), the name is GRAND_CENTRAL (already
     *     taken), the configuration's cue size is too big, or the configuration
     *     needs a select class name
     * @exception EtExistsException
     *     if the station already exists but with a different configuration
     * @exception EtTooManyException
     *     if the maximum number of stations has been created already
     */
    synchronized public Station createStation(StationConfig config, String name,
                                              int position, int parallelPosition)
            throws IOException, EtException,
                   EtExistsException, EtTooManyException {

        // cannot create GrandCentral
        if (name.equals("GRAND_CENTRAL")) {
            throw new EtException("Cannot create GRAND_CENTRAL station");
        }

        // check value of position
        if (position != Constants.end && position < 1) {
            throw new EtException("bad value for position");
        }

        // check value of parallel position
        if ((parallelPosition != Constants.end) &&
            (parallelPosition != Constants.newHead) &&
            (parallelPosition  < 0)) {
            throw new EtException("bad value for parallel position");
        }

        // check station configuration for self consistency
        configCheck(config);

        // command
        out.writeInt(Constants.netStatCrAt);

        // station configuration
        out.writeInt(Constants.structOk); // not used in Java
        out.writeInt(config.getFlowMode());
        out.writeInt(config.getUserMode());
        out.writeInt(config.getRestoreMode());
        out.writeInt(config.getBlockMode());
        out.writeInt(config.getPrescale());
        out.writeInt(config.getCue());
        out.writeInt(config.getSelectMode());
        int[] select = config.getSelect();
        for (int i=0; i < Constants.stationSelectInts; i++) {
            out.writeInt(select[i]);
        }

        int functionLength = 0; // no function
        if (config.getSelectFunction() != null) {
            functionLength = config.getSelectFunction().length() + 1;
        }
        out.writeInt(functionLength);

        int libraryLength = 0; // no lib
        if (config.getSelectLibrary() != null) {
            libraryLength = config.getSelectLibrary().length() + 1;
        }
        out.writeInt(libraryLength);

        int classLength = 0; // no class
        if (config.getSelectClass() != null) {
            classLength = config.getSelectClass().length() + 1;
        }
        out.writeInt(classLength);

        // station name and position
        int nameLength = name.length() + 1;
        out.writeInt(nameLength);
        out.writeInt(position);
        out.writeInt(parallelPosition);

        // write string(s)
        try {
            if (functionLength > 0) {
                out.write(config.getSelectFunction().getBytes("ASCII"));
                out.writeByte(0);
            }
            if (libraryLength > 0) {
                out.write(config.getSelectLibrary().getBytes("ASCII"));
                out.writeByte(0);
            }
            if (classLength > 0) {
                out.write(config.getSelectClass().getBytes("ASCII"));
                out.writeByte(0);
            }
            out.write(name.getBytes("ASCII"));
            out.writeByte(0);
        }
        catch (UnsupportedEncodingException ex) { /* never happen */ }

        out.flush();

        int err = in.readInt();
        int statId = in.readInt();

        if (err ==  Constants.errorTooMany) {
            throw new EtTooManyException("maximum number of stations already created");
        }
        else if (err == Constants.errorExists) {
            throw new EtExistsException("station already exists with different definition");
        }
        else if (err ==  Constants.error) {
            throw new EtException("trying to add incompatible parallel station, or\n" +
                    "trying to add parallel station to head of existing parallel group, or\n" +
                    "cannot load select class");
        }

        // create station
        Station station = new Station(name, statId, this);
        station.usable = true;
        if (debug >= Constants.debugInfo) {
            System.out.println("creating station " + name + " is done");
        }
        
        return station;
    }


    /**
     * Removes an existing station.
     *
     * @param station station object
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if attachments to the station still exist, the station is GRAND_CENTRAL
     *     (which must always exist), the station does not exist, or the
     *     station object is invalid
     */
    synchronized public void removeStation(Station station) throws IOException, EtException {

        // cannot remove GrandCentral
        if (station.id == 0) {
            throw new EtException("Cannot remove GRAND_CENTRAL station");
        }

        // station object invalid
        if (!station.usable || station.sys != this) {
            throw new EtException("Invalid station");
        }

        out.writeInt(Constants.netStatRm);
        out.writeInt(station.id);
        out.flush();

        int err = in.readInt();
        if (err ==  Constants.error) {
            throw new EtException("Either no such station exists " +
                                  "or remove all attachments before removing station");
        }
        
        station.usable = false;
    }


  /**
   * Changes the position of a station in the ordered list of stations.
   *
   * @param station   station object
   * @param position  position in the main station list (starting at 0)
   * @param parallelPosition  position in list of parallel stations (starting at 0)
   *
   * @exception java.io.IOException
   *     if problems with network comunications
   * @exception EtException
   *     if the station does not exist, trying to move GRAND_CENTRAL, position
   *     is < 1 (GRAND_CENTRAL is always first), parallelPosition < 0, 
   *     station object is invalid,
   *     trying to move an incompatible parallel station to an existing group
   *     of parallel stations or to the head of an existing group of parallel
   *     stations.
   */
  synchronized public void setStationPosition(Station station, int position,
                                              int parallelPosition)
          throws IOException, EtException {

      // cannot move GrandCentral
      if (station.id == 0) {
          throw new EtException("Cannot move GRAND_CENTRAL station");
      }

      if ((position != Constants.end) && (position < 0)) {
          throw new EtException("bad value for position");
      }
      else if (position == 0) {
          throw new EtException("GRAND_CENTRAL station is always first");
      }

      if ((parallelPosition != Constants.end) &&
          (parallelPosition != Constants.newHead) &&
          (parallelPosition < 0)) {
          throw new EtException("bad value for parallelPosition");
      }

      if (!station.usable || station.sys != this) {
          throw new EtException("Invalid station");
      }

      out.writeInt(Constants.netStatSPos);
      out.writeInt(station.id);
      out.writeInt(position);
      out.writeInt(parallelPosition);
      out.flush();

      int err = in.readInt();
      if (err ==  Constants.error) {
          station.usable = false;
          throw new EtException("station does not exist");
      }
  }


    /**
     * Gets the position of a station in the ordered list of stations.
     *
     * @param station station object
     * @return position of a station in the main linked list of stations
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if the station does not exist, or station object is invalid
     */
    synchronized public int getStationPosition(Station station)
            throws IOException, EtException {

        // GrandCentral is always first
        if (station.id == 0) {
            return 0;
        }

        if (!station.usable || station.sys != this) {
            throw new EtException("Invalid station");
        }

        out.writeInt(Constants.netStatGPos);
        out.writeInt(station.id);
        out.flush();

        int err = in.readInt();
        int position = in.readInt();
        // skip parallel position info
        in.skipBytes(4);
        if (err ==  Constants.error) {
            station.usable = false;
            throw new EtException("station does not exist");
        }
        
        return position;
    }


    /**
     * Gets the position of a parallel station in its ordered list of
     * parallel stations.
     *
     * @param station station object
     * @return position of a station in the linked list of stations
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if the station does not exist, or station object is invalid
     */
    synchronized public int getStationParallelPosition(Station station)
            throws IOException, EtException {

        // parallel position is 0 for serial stations (like GrandCentral)
        if (station.id == 0) {
            return 0;
        }

        if (!station.usable || station.sys != this) {
            throw new EtException("Invalid station");
        }

        out.writeInt(Constants.netStatGPos);
        out.writeInt(station.id);
        out.flush();

        int err = in.readInt();
        // skip main position info
        in.skipBytes(4);
        int pPosition = in.readInt();
        if (err ==  Constants.error) {
            station.usable = false;
            throw new EtException("station does not exist");
        }

        return pPosition;
    }


    /**
     * Create an attachment to a station.
     *
     * @param station station object
     * @return an attachment object
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if the station does not exist, or station object is invalid
     * @exception EtTooManyException
     *     if no more attachments are allowed to the station, or
     *     if no more attachments are allowed to ET system
     */
    synchronized public Attachment attach(Station station)
            throws IOException, EtException, EtTooManyException {

        if (!station.usable || station.sys != this) {
            throw new EtException("Invalid station");
        }

        // find name of our host
        String host = "unknown";
        try {host = InetAddress.getLocalHost().getHostName();}
        catch (UnknownHostException ex) { /* host = "unknown" */ }

        out.writeInt(Constants.netStatAtt);
        out.writeInt(station.id);
        out.writeInt(-1); // no pid in Java
        out.writeInt(host.length() + 1);

        // write host string
        try {
            out.write(host.getBytes("ASCII"));
            out.writeByte(0);
        }
        catch (UnsupportedEncodingException ex) { /* never happen */ }
        out.flush();

        int err = in.readInt();
        int attId = in.readInt();
        if (err ==  Constants.error) {
            station.usable = false;
            throw new EtException("station does not exist");
        }
        else if (err ==  Constants.errorTooMany) {
            throw new EtTooManyException("no more attachments allowed to either station or system");
        }

        Attachment att = new Attachment(station, attId, this);
        att.usable = true;
        return att;
    }



    /**
     * Remove an attachment from a station.
     *
     * @param att attachment object
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if the attachment object is invalid
     */
    synchronized public void detach(Attachment att)
            throws IOException, EtException {

        if (!att.usable || att.sys != this) {
            throw new EtException("Invalid attachment");
        }

        out.writeInt(Constants.netStatDet);
        out.writeInt(att.id);
        out.flush();
        // always returns ok
        in.readInt();
        att.usable = false;
    }


  //*****************************************************
  //                STATION INFORMATION                 *
  //*****************************************************


    /**
     * Is given attachment attached to a station?
     *
     * @param station  station object
     * @param att      attachment object
     *
     * @return <code>true</code> if an attachment is attached to a station,
     *         and <code>false</code> otherwise
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if the station does not exist, station object is invalid, or attachment
     *     object is invalid
     */
    synchronized public boolean stationAttached(Station station, Attachment att)
            throws IOException, EtException {

        if (!station.usable || station.sys != this) {
            throw new EtException("Invalid station");
        }

        if (!att.usable || att.sys != this) {
            throw new EtException("Invalid attachment");
        }

        out.writeInt(Constants.netStatIsAt);
        out.writeInt(station.id);
        out.writeInt(att.id);
        out.flush();
        int err = in.readInt();
        if (err == Constants.error) {
            station.usable = false;
            throw new EtException("station does not exist");
        }
        
        return (err == 1);
    }


    /**
     * Does given station exist?
     *
     * @param name station name
     * @return <code>true</code> if a station exists, and
     *         <code>false</code> otherwise
     * @exception java.io.IOException
     *     if problems with network comunications
     */
    synchronized public boolean stationExists(String name)
            throws IOException {

        out.writeInt(Constants.netStatEx);
        out.writeInt(name.length()+1);
        try {
            out.write(name.getBytes("ASCII"));
            out.writeByte(0);
        }
        catch (UnsupportedEncodingException ex) {}
        out.flush();
        int err = in.readInt();
        // skip main position info
        in.skipBytes(4);
        // id is ignored here since we can't return it as a C function can
        return (err == 1);
    }


    /**
     * Gets a station's object representation from its name.
     *
     * @param name station name
     * @return station object
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if the station does not exist
     */
    synchronized public Station stationNameToObject(String name)
            throws IOException, EtException {

        out.writeInt(Constants.netStatEx);
        out.writeInt(name.length()+1);
        try {
            out.write(name.getBytes("ASCII"));
            out.writeByte(0);
        }
        catch (UnsupportedEncodingException ex) { /* never happen */ }
        out.flush();

        int err = in.readInt();
        int statId = in.readInt();
        if (err == 1) {
            Station stat = new Station(name, statId, this);
            stat.usable = true;
            return stat;
        }
        throw new EtException("station " + name + " does not exist");
    }


    //*****************************************************
    //                       EVENTS
    //*****************************************************


    /**
     * Get new (unused) events from an ET system.
     *
     * @param att       attachment object
     * @param mode      if there are no events available, this parameter specifies
     *                  whether to wait for some by sleeping, by waiting for a set
     *                  time, or by returning immediately
     * @param microSec  the number of microseconds to wait if a timed wait is
     *                  specified
     * @param count     the number of events desired
     * @param size      the size of events in bytes
     *
     * @return an array of new events
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if arguments have bad values, attachment object is invalid, or other general errors
     * @exception EtDeadException
     *     if the ET system processes are dead
     * @exception EtEmptyException
     *     if the mode is asynchronous and the station's input list is empty
     * @exception EtBusyException
     *     if the mode is asynchronous and the station's input list is being used
     *     (the mutex is locked)
     * @exception EtTimeoutException
     *     if the mode is timed wait and the time has expired
     * @exception EtWakeUpException
     *     if the attachment has been commanded to wakeup,
     *     {@link org.jlab.coda.et.system.EventList#wakeUp}, {@link org.jlab.coda.et.system.EventList#wakeUpAll}
     */
    public Event[] newEvents(Attachment att, int mode, int microSec, int count, int size)
            throws IOException, EtException, EtDeadException,
                   EtEmptyException, EtBusyException,
                   EtTimeoutException, EtWakeUpException  {

        return newEvents(att, mode, microSec, count, size, 1);
    }


    /**
     * Get new (unused) events from a specified group of such events in an ET system.
     * This method uses JNI to call ET routines in the C library. Event memory is
     * directly accessed shared memory.
     *
     * @param attId    attachment id number
     * @param mode     if there are no events available, this parameter specifies
     *                 whether to wait for some by sleeping, by waiting for a set
     *                 time, or by returning immediately
     * @param sec      the number of seconds to wait if a timed wait is specified
     * @param nsec     the number of nanoseconds to wait if a timed wait is specified
     * @param count    the number of events desired
     * @param size     the size of events in bytes
     * @param group    the group number of events
     *
     * @return an array of new events
     *
     * @exception EtException
     *     if arguments have bad values, attachment object is invalid, or other general errors
     * @exception EtDeadException
     *     if the ET system processes are dead
     * @exception EtEmptyException
     *     if the mode is asynchronous and the station's input list is empty
     * @exception EtBusyException
     *     if the mode is asynchronous and the station's input list is being used
     *     (the mutex is locked)
     * @exception EtTimeoutException
     *     if the mode is timed wait and the time has expired
     * @exception EtWakeUpException
     *     if the attachment has been commanded to wakeup,
     *     {@link org.jlab.coda.et.system.EventList#wakeUp}, {@link org.jlab.coda.et.system.EventList#wakeUpAll}
     */
    private Event[] newEventsJNI(int attId, int mode, int sec, int nsec, int count, int size, int group)
            throws EtException,        EtDeadException, EtWakeUpException,
                   EtTimeoutException, EtBusyException, EtEmptyException  {

        EventImpl[] events = sys.getJni().newEvents(sys.getJni().getLocalEtId(), attId,
                                                    mode, sec, nsec, count, size, group);

        // set all events' data arrays to point to shared memory correctly

        // Start with the whole data buffer and slice it -
        // create smaller buffers with the SAME underlying data
        MappedByteBuffer buffer = sys.getBuffer();
        int position, eventSize = (int) sys.getEventSize();

        for (EventImpl ev : events) {
            position = ev.getId() * eventSize; // id corresponds to nth place in shared memory
            buffer.position(position);
            buffer.limit(position + eventSize);
            ev.setDataBuffer(buffer.slice());
        }

        return events;
    }




    /**
     * Get new (unused) events from a specified group of such events in an ET system.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att       attachment object
     * @param mode      if there are no events available, this parameter specifies
     *                  whether to wait for some by sleeping, by waiting for a set
     *                  time, or by returning immediately
     * @param microSec  the number of microseconds to wait if a timed wait is
     *                  specified
     * @param count     the number of events desired
     * @param size      the size of events in bytes
     * @param group     the group number of events
     *
     * @return an array of events
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if arguments have bad values, attachment object is invalid, or other general errors
     * @exception EtDeadException
     *     if the ET system processes are dead
     * @exception EtEmptyException
     *     if the mode is asynchronous and the station's input list is empty
     * @exception EtBusyException
     *     if the mode is asynchronous and the station's input list is being used
     *     (the mutex is locked)
     * @exception EtTimeoutException
     *     if the mode is timed wait and the time has expired
     * @exception EtWakeUpException
     *     if the attachment has been commanded to wakeup,
     *     {@link org.jlab.coda.et.system.EventList#wakeUp}, {@link org.jlab.coda.et.system.EventList#wakeUpAll}
     */
    synchronized public Event[] newEvents(Attachment att, int mode, int microSec,
                                          int count, int size, int group)
            throws IOException, EtException, EtDeadException,
                   EtEmptyException,   EtBusyException,
                   EtTimeoutException, EtWakeUpException  {

        if (att == null || !att.usable || att.sys != this) {
            throw new EtException("Invalid attachment");
        }

        if (count == 0) {
            return new Event[0];
        }

        int wait = mode & Constants.waitMask;
        if ((wait != Constants.sleep) &&
            (wait != Constants.timed) &&
            (wait != Constants.async))  {
            throw new EtException("bad mode argument");
        }
        else if ((microSec < 0) && (wait == Constants.timed)) {
            throw new EtException("bad microSec argument");
        }
        else if (size < 1) {
            throw new EtException("bad size argument");
        }
        else if (count < 0) {
            throw new EtException("bad count argument");
        }
        else if (group < 1) {
            throw new EtException("group number must be > 0");
        }

        int sec  = 0;
        int nsec = 0;
        if (microSec > 0) {
            sec = microSec/1000000;
            nsec = (microSec - sec*1000000) * 1000;
        }

        // Do we get things locally through JNI?
        if (sys.isMapLocalSharedMemory()) {
            return newEventsJNI(att.getId(), mode, sec, nsec, count, size, group);
        }

        byte[] buffer = new byte[36];
        Utils.intToBytes(Constants.netEvsNewGrp, buffer, 0);
        Utils.intToBytes(att.id,      buffer, 4);
        Utils.intToBytes(mode,        buffer, 8);
        Utils.longToBytes((long)size, buffer, 12);
        Utils.intToBytes(count,       buffer, 20);
        Utils.intToBytes(group,       buffer, 24);
        Utils.intToBytes(sec,         buffer, 28);
        Utils.intToBytes(nsec,        buffer, 32);
        out.write(buffer);
        out.flush();

        // ET system clients are liable to get stuck here if the ET
        // system crashes. So use the 2 second socket timeout to try
        // to read again. If the socket connection has been broken,
        // an IOException will be generated.
        int err;
        while (true) {
            try {
                err = in.readInt();
                break;
            }
            // If there's an interrupted ex, socket is OK, try again.
            catch (InterruptedIOException ex) {
            }
        }

        if (err < Constants.ok) {
            if (debug >= Constants.error) {
                System.out.println("error in ET system");
            }

            // throw some exceptions
            if (err == Constants.error) {
                throw new EtException("bad mode value" );
            }
            else if (err == Constants.errorBusy) {
                throw new EtBusyException("input list is busy");
            }
            else if (err == Constants.errorEmpty) {
                throw new EtEmptyException("no events in list");
            }
            else if (err == Constants.errorWakeUp) {
                throw new EtWakeUpException("attachment " + att.id + " woken up");
            }
            else if (err == Constants.errorTimeout) {
                throw new EtTimeoutException("timed out");
            }
        }

        // number of events to expect
        int numEvents = err;

        // list of events to return
        EventImpl[] evs = new EventImpl[numEvents];
        buffer = new byte[4*numEvents];
        in.readFully(buffer, 0, 4*numEvents);

        int index=-4;
        long sizeLimit = (size > sys.getEventSize()) ? (long)size : sys.getEventSize();
        final int modify = Constants.modify;

        // Java limits array sizes to an integer. Thus we're limited to
        // 2G byte arrays. Essentially Java is a 32 bit system in this
        // regard even though the JVM might be 64 bits.
        // So set limits on the size accordingly.

        // if C ET system we are connected to is 64 bits ...
        if (!isJava && sys.isBit64()) {
            // if events size > ~1G, only allocate what's asked for
            if ((long)numEvents*sys.getEventSize() > Integer.MAX_VALUE/2) {
                sizeLimit = size;
            }
        }

        for (int j=0; j < numEvents; j++) {
            evs[j] = new EventImpl(size, (int)sizeLimit, isJava);
            evs[j].setId(Utils.bytesToInt(buffer, index+=4));
            evs[j].setModify(modify);
            evs[j].setOwner(att.getId());
        }

        return evs;
    }



    /**
     * Get events from an ET system.
     * This method uses JNI to call ET routines in the C library. Event memory is
     * directly accessed shared memory.
     *
     * @param attId    attachment id number
     * @param mode     if there are no events available, this parameter specifies
     *                 whether to wait for some by sleeping, by waiting for a set
     *                 time, or by returning immediately
     * @param sec      the number of seconds to wait if a timed wait is specified
     * @param nsec     the number of nanoseconds to wait if a timed wait is specified
     * @param count    the number of events desired
     *
     * @return an array of events
     *
     * @exception EtException
     *     if arguments have bad values, the attachment's station is
     *     GRAND_CENTRAL, or the attachment object is invalid, or other general errors
     * @exception EtDeadException
     *     if the ET system processes are dead
     * @exception EtEmptyException
     *     if the mode is asynchronous and the station's input list is empty
     * @exception EtBusyException
     *     if the mode is asynchronous and the station's input list is being used
     *     (the mutex is locked)
     * @exception EtTimeoutException
     *     if the mode is timed wait and the time has expired
     * @exception EtWakeUpException
     *     if the attachment has been commanded to wakeup,
     *     {@link org.jlab.coda.et.system.EventList#wakeUp}, {@link org.jlab.coda.et.system.EventList#wakeUpAll}
     */
    private Event[] getEventsJNI(int attId, int mode, int sec, int nsec, int count)
            throws EtException  {

        EventImpl[] events = sys.getJni().getEvents(sys.getJni().getLocalEtId(), attId, mode, sec, nsec, count);

        // set all events' data arrays to point to shared memory correctly

        // Start with the whole data buffer and slice it -
        // create smaller buffers with the SAME underlying data
        MappedByteBuffer buffer = sys.getBuffer();
        int position, eventSize = (int) sys.getEventSize();

        for (EventImpl ev : events) {
            position = ev.getId() * eventSize; // id corresponds to nth place in shared memory
            buffer.position(position);
            buffer.limit(position + eventSize);
            ev.setDataBuffer(buffer.slice());
        }

        return events;
    }



    /**
     * Get events from an ET system.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att      attachment object
     * @param mode     if there are no events available, this parameter specifies
     *                 whether to wait for some by sleeping, by waiting for a set
     *                 time, or by returning immediately
     * @param microSec the number of microseconds to wait if a timed wait is
     *                 specified
     * @param count    the number of events desired
     *
     * @return an array of events
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if arguments have bad values, the attachment's station is
     *     GRAND_CENTRAL, or the attachment object is invalid, or other general errors
     * @exception EtDeadException
     *     if the ET system processes are dead
     * @exception EtEmptyException
     *     if the mode is asynchronous and the station's input list is empty
     * @exception EtBusyException
     *     if the mode is asynchronous and the station's input list is being used
     *     (the mutex is locked)
     * @exception EtTimeoutException
     *     if the mode is timed wait and the time has expired
     * @exception EtWakeUpException
     *     if the attachment has been commanded to wakeup,
     *     {@link org.jlab.coda.et.system.EventList#wakeUp}, {@link org.jlab.coda.et.system.EventList#wakeUpAll}
     */
    synchronized public Event[] getEvents(Attachment att, int mode, int microSec, int count)
            throws IOException, EtException,
                   EtEmptyException, EtBusyException,
                   EtTimeoutException, EtWakeUpException  {

        if (att == null|| !att.usable || att.sys != this) {
            throw new EtException("Invalid attachment");
        }

        // may not get events from GrandCentral
        if (att.station.id == 0) {
            throw new EtException("may not get events from GRAND_CENTRAL");
        }

        if (count == 0) {
            return new Event[0];
        }

        int wait = mode & Constants.waitMask;
        if ((wait != Constants.sleep) &&
            (wait != Constants.timed) &&
            (wait != Constants.async))  {
            throw new EtException("bad mode argument");
        }
        else if ((microSec < 0) && (wait == Constants.timed)) {
            throw new EtException("bad microSec argument");
        }
        else if (count < 0) {
            throw new EtException("bad count argument");
        }

        // Modifying the whole event has precedence over modifying
        // only the header should the user specify both.
        int modify = mode & Constants.modify;
        if (modify == 0) {
            modify = mode & Constants.modifyHeader;
        }

        int sec  = 0;
        int nsec = 0;
        if (microSec > 0) {
            sec = microSec/1000000;
            nsec = (microSec - sec*1000000) * 1000;
        }

        // Do we get things locally through JNI?
        if (sys.isMapLocalSharedMemory()) {
            return getEventsJNI(att.getId(), mode, sec, nsec, count);
        }

        // Or do we go through the network?
        byte[] buffer = new byte[28];
        Utils.intToBytes(Constants.netEvsGet, buffer, 0);
        Utils.intToBytes(att.id, buffer, 4);
        Utils.intToBytes(wait,   buffer, 8);
        Utils.intToBytes(modify, buffer, 12);
        Utils.intToBytes(count,  buffer, 16);
        Utils.intToBytes(sec,    buffer, 20);
        Utils.intToBytes(nsec,   buffer, 24);
        out.write(buffer);
        out.flush();

        // ET system clients are liable to get stuck here if the ET
        // system crashes. So use the 2 second socket timeout to try
        // to read again. If the socket connection has been broken,
        // an IOException will be generated.
        int err;
        while (true) {
            try {
                err = in.readInt();
                break;
            }
            // If there's an interrupted ex, socket is OK, try again.
            catch (InterruptedIOException ex) {
            }
        }

        if (err < Constants.ok) {
            if (debug >= Constants.error) {
                System.out.println("error in ET system");
            }

            if (err == Constants.error) {
                throw new EtException("bad mode value" );
            }
            else if (err == Constants.errorBusy) {
                throw new EtBusyException("input list is busy");
            }
            else if (err == Constants.errorEmpty) {
                throw new EtEmptyException("no events in list");
            }
            else if (err == Constants.errorWakeUp) {
                throw new EtWakeUpException("attachment " + att.id + " woken up");
            }
            else if (err == Constants.errorTimeout) {
                throw new EtTimeoutException("timed out");
            }
        }

        // skip reading total size (long)
        in.skipBytes(8);

        final int selectInts   = Constants.stationSelectInts;
        final int dataShift    = Constants.dataShift;
        final int dataMask     = Constants.dataMask;
        final int priorityMask = Constants.priorityMask;

        int numEvents = err;
        EventImpl[] evs = new EventImpl[numEvents];
        int byteChunk = 4*(9+Constants.stationSelectInts);
        buffer = new byte[byteChunk];
        int index;

        long  length, memSize;
        int   priAndStat;

        for (int j=0; j < numEvents; j++) {
            in.readFully(buffer, 0, byteChunk);

            length  = Utils.bytesToLong(buffer, 0);
            memSize = Utils.bytesToLong(buffer, 8);

            // Note that the server will not send events too big for us,
            // we'll get an error above.

            // if C ET system we are connected to is 64 bits ...
            if (!isJava && sys.isBit64()) {
                // if event size > ~1G, only allocate enough to hold data
                if (memSize > Integer.MAX_VALUE/2) {
                    memSize = length;
                }
            }
            evs[j] = new EventImpl((int)memSize, (int)memSize, isJava);
            evs[j].setLength((int)length);
            priAndStat = Utils.bytesToInt(buffer, 16);
            evs[j].setPriority(priAndStat & priorityMask);
            evs[j].setDataStatus((priAndStat & dataMask) >> dataShift);
            evs[j].setId(Utils.bytesToInt(buffer, 20));
            // skip unused int here
            evs[j].setByteOrder(Utils.bytesToInt(buffer, 28));
            index = 32;   // skip unused int
            int[] control = new int[selectInts];
            for (int i=0; i < selectInts; i++) {
                control[i] = Utils.bytesToInt(buffer, index+=4);
            }
            evs[j].setControl(control);
            evs[j].setModify(modify);
            evs[j].setOwner(att.getId());

            in.readFully(evs[j].getData(), 0, (int)length);
        }

        return evs;
    }



    /**
     * Put events into an ET system.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att         attachment object
     * @param eventList   list of event objects
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if events are not owned by this attachment or the attachment object
     *     is invalid; if offset and/or length arg is not valid
     * @exception EtDeadException
     *     if the ET system processes are dead
     */
    public void putEvents(Attachment att, List<Event> eventList)
            throws IOException, EtException {
        putEvents(att, eventList.toArray(new Event[eventList.size()]));
    }


    /**
     * Put events into an ET system.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att   attachment object
     * @param evs   array of event objects
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if events are not owned by this attachment or the attachment object
     *     is invalid; if offset and/or length arg is not valid
     * @exception EtDeadException
     *     if the ET system processes are dead
     */
    public void putEvents(Attachment att, Event[] evs)
            throws IOException, EtException {
        putEvents(att, evs, 0, evs.length);
    }


    /**
     * Put events into an ET system.
     * This method uses JNI to call ET routines in the C library. Since event memory, in
     * this case, is directly accessed shared memory, none of it is copied.
     *
     * @param attId  attachment ID
     * @param evs    array of event objects
     * @param offset offset into array
     * @param length number of array elements to put
     *
     * @exception EtException
     *     if events are not owned by this attachment or the attachment object
     *     is invalid; if offset and/or length arg is not valid
     * @exception EtDeadException
     *     if the ET system processes are dead
     */
    private void putEventsJNI(int attId, Event[] evs, int offset, int length)
            throws EtException {

        // C interface has no offset (why did I do that again?), so compensate for that
        EventImpl[] events = new EventImpl[length];
        for (int i=0; i<length; i++) {
            events[i] = (EventImpl) evs[offset + i];
        }

        sys.getJni().putEvents(sys.getJni().getLocalEtId(), attId, events, length);
    }


    /**
     * Put events into an ET system.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att    attachment object
     * @param evs    array of event objects
     * @param offset offset into array
     * @param length number of array elements to put
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if events are not owned by this attachment or the attachment object
     *     is invalid; if offset and/or length arg is not valid
     * @exception EtDeadException
     *     if the ET system processes are dead
     */
    synchronized public void putEvents(Attachment att, Event[] evs, int offset, int length)
            throws IOException, EtException {

        if (offset < 0 || length < 0 || offset + length > evs.length) {
            throw new EtException("Bad offset or length argument(s)");
        }

        if (att == null || !att.usable || att.sys != this) {
            throw new EtException("Invalid attachment");
        }

        final int selectInts = Constants.stationSelectInts;
        final int dataShift  = Constants.dataShift;
        final int modify     = Constants.modify;

        // find out how many events we're sending & total # bytes
        int bytes = 0, numEvents = 0;
        int headerSize = 4*(7+selectInts);

        for (int i=offset; i < length; i++) {
            // each event must be registered as owned by this attachment
            if (evs[i].getOwner() != att.id) {
                throw new EtException("may not put event(s), not owner");
            }
            // if modifying header only or header & data ...
            if (evs[i].getModify() > 0) {
                numEvents++;
                bytes += headerSize;
                // if modifying data as well ...
                if (evs[i].getModify() == modify) {
                    bytes += evs[i].getLength();
                }
            }
        }


        // Did we get things locally through JNI?
        if (sys.isMapLocalSharedMemory()) {
            putEventsJNI(att.getId(), evs, offset, length);
            return;
        }


        out.writeInt(Constants.netEvsPut);
        out.writeInt(att.id);
        out.writeInt(numEvents);
        out.writeLong((long)bytes);

        for (int i=offset; i < length; i++) {
            // send only if modifying an event (data or header) ...
            if (evs[i].getModify() > 0) {
                out.writeInt(evs[i].getId());
                out.writeInt(0); // not used
                out.writeLong((long)evs[i].getLength());
                out.writeInt(evs[i].getPriority() | evs[i].getDataStatus() << dataShift);
                out.writeInt(evs[i].getByteOrder());
                out.writeInt(0); // not used
                int[] control = evs[i].getControl();
                for (int j=0; j < selectInts; j++) {
                    out.writeInt(control[j]);
                }

                // send data only if modifying whole event
                if (evs[i].getModify() == modify) {
//System.out.println("Sending data = " + Utils.bytesToInt(evs[i].getData(),0) + ", len = " + evs[i].getLength());
                    ByteBuffer buf = evs[i].getDataBuffer();
                    // buf should never be null
                    if (!buf.hasArray()) {
                        System.out.println("Memory mapped buffer does NOT have a backing array !!!");
                        for (int j=0; j<evs[i].getLength(); j++) {
                            out.write(buf.get(j));
                        }
                    }
                    else {
                        out.write(buf.array(), 0, evs[i].getLength());
                    }
                }
            }
        }

        out.flush();

        // err should always be = Constants.ok
        // skip reading error
        in.skipBytes(4);

        return;
    }



    /**
     * Dump events into an ET system.
     * This method uses JNI to call ET routines in the C library.
     *
     * @param attId  attachment ID
     * @param evs    array of event objects
     * @param offset offset into array
     * @param length number of array elements to put
     *
     * @exception EtException
     *     if events are not owned by this attachment or the attachment object
     *     is invalid
     * @exception EtDeadException
     *     if the ET system processes are dead
     */
    private void dumpEventsJNI(int attId, Event[] evs, int offset, int length)
            throws EtException {

        // C interface has no offset (why did I do that again?), so compensate for that
        EventImpl[] events = new EventImpl[length];
        for (int i=0; i<length; i++) {
            events[i] = (EventImpl) evs[offset + i];
        }

        sys.getJni().dumpEvents(sys.getJni().getLocalEtId(), attId, events, length);
    }


    /**
     * Dispose of unwanted events in an ET system. The events are recycled and not
     * made available to any other user.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att   attachment object
     * @param evs   array of event objects
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if events are not owned by this attachment or the attachment object
     *     is invalid
     * @exception EtDeadException
     *     if the ET system processes are dead
     */
    public void dumpEvents(Attachment att, Event[] evs)
            throws IOException, EtException {
        dumpEvents(att, evs, 0, evs.length);
    }



    /**
     * Dispose of unwanted events in an ET system. The events are recycled and not
     * made available to any other user.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att         attachment object
     * @param eventList   list of event objects
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if events are not owned by this attachment or the attachment object
     *     is invalid
     * @exception EtDeadException
     *     if the ET system processes are dead
     */
    public void dumpEvents(Attachment att, List<Event> eventList)
            throws IOException, EtException {
        dumpEvents(att, eventList.toArray(new Event[eventList.size()]));
    }


    /**
     * Dispose of unwanted events in an ET system. The events are recycled and not
     * made available to any other user.
     * Will access local C-based ET systems through JNI/shared memory, but other ET
     * systems through sockets.
     *
     * @param att         attachment object
     *
     * @exception java.io.IOException
     *     if problems with network comunications
     * @exception EtException
     *     if events are not owned by this attachment or the attachment object
     *     is invalid
     * @exception EtDeadException
     *     if the ET system processes are dead
     */
    synchronized public void dumpEvents(Attachment att, Event[] evs, int offset, int length)
            throws IOException, EtException {

        if (att == null || !att.usable || att.sys != this) {
            throw new EtException("Invalid attachment");
        }

        // find out how many we're sending
        int numEvents = 0;
        for (int i=offset; i<offset+length; i++) {
            // each event must be registered as owned by this attachment
            if (evs[i].getOwner() != att.id) {
                throw new EtException("may not put event(s), not owner");
            }
            if (evs[i].getModify() > 0) numEvents++;
        }

        // Did we get things locally through JNI?
        if (sys.isMapLocalSharedMemory()) {
            dumpEventsJNI(att.getId(), evs, offset, length);
            return;
        }

        out.writeInt(Constants.netEvsDump);
        out.writeInt(att.id);
        out.writeInt(numEvents);

        for (int i=offset; i<offset+length; i++) {
            // send only if modifying an event (data or header) ...
            if (evs[i].getModify() > 0) {
                out.writeInt(evs[i].getId());
            }
        }
        out.flush();

        // err should always be = Constants.ok
        // skip reading error
        in.skipBytes(4);

        return;
    }


    //****************************************
    //                Getters                *
    //****************************************

    /**
     * Gets "integer" format ET system data over the network.
     *
     * @param cmd coded command to send to the TCP server thread.
     * @return integer value
     * @exception java.io.IOException
     *     if there are problems with network communication
     */
    synchronized private int getIntValue(int cmd) throws IOException {
        out.writeInt(cmd);
        out.flush();
        // err should always be = Constants.ok
        // skip reading error
        in.skipBytes(4);
        // return val (next readInt)
        return in.readInt();
    }


    /**
     * Gets the number of stations in the ET system.
     *
     * @return number of stations in the ET system
     * @exception java.io.IOException
     *     if there are problems with network communication
     */
    public int getNumStations() throws IOException {
        return getIntValue(Constants.netSysStat);
    }


    /**
     * Gets the maximum number of stations allowed in the ET system.
     *
     * @return maximum number of stations allowed in the ET system
     * @exception java.io.IOException
     *     if there are problems with network communication
     */
    public int getStationsMax() throws IOException {
        return getIntValue(Constants.netSysStatMax);
    }


    /**
     * Gets the number of attachments in the ET system.
     *
     * @return number of attachments in the ET system
     * @exception java.io.IOException
     *     if there are problems with network communication
     */
    public int getNumAttachments() throws IOException {
        return getIntValue(Constants.netSysAtt);
    }


    /**
     * Gets the maximum number of attachments allowed in the ET system.
     *
     * @return maximum number of attachments allowed in the ET system
     * @exception java.io.IOException
     *     if there are problems with network communication
     */
    public int getAttachmentsMax() throws IOException {
        return getIntValue(Constants.netSysAttMax);
    }


    /**
     * Gets the number of local processes in the ET system.
     * This is only relevant in C-language, Solaris systems.
     *
     * @return number of processes in the ET system
     * @exception java.io.IOException
     *     if there are problems with network communication
     */
    public int getNumProcesses() throws IOException {
        return getIntValue(Constants.netSysProc);
    }


    /**
     * Gets the maximum number of local processes allowed in the ET system.
     * This is only relevant in C-language, Solaris systems.
     *
     * @return maximum number of processes allowed in the ET system
     * @exception java.io.IOException
     *     if there are problems with network communication
     */
    public int getProcessesMax() throws IOException {
        return getIntValue(Constants.netSysProcMax);
    }


    /**
     * Gets the number of temp events in the ET system.
     * This is only relevant in C-language systems.
     *
     * @return number of temp events in the ET system
     * @exception java.io.IOException
     *     if there are problems with network communication
     */
    public int getNumTemps() throws IOException {
        return getIntValue(Constants.netSysTmp);
    }


    /**
     * Gets the maximum number of temp events allowed in the ET system.
     * This is only relevant in C-language systems.
     *
     * @return maximum number of temp events in the ET system
     * @exception java.io.IOException
     *     if there are problems with network communication
     */
    public int getTempsMax() throws IOException {
        return getIntValue(Constants.netSysTmpMax);
    }


    /**
     * Gets the ET system heartbeat. This is only relevant in
     * C-language systems.
     *
     * @return ET system heartbeat
     * @exception java.io.IOException
     *     if there are problems with network communication
     */
    public int getHeartbeat() throws IOException {
        return getIntValue(Constants.netSysHBeat);
    }


    /**
     * Gets the UNIX pid of the ET system process.
     * This is only relevant in C-language systems.
     *
     * @return UNIX pid of the ET system process
     * @exception java.io.IOException
     *     if there are problems with network communication
     */
    public int getPid() throws IOException {
        return getIntValue(Constants.netSysPid);
    }


    /**
     * Gets the number of events in the ET system.
     * @return number of events in the ET system
     */
    public int getNumEvents() {
        return sys.getNumEvents();
    }


    /**
     * Gets the "normal" event size in bytes.
     * @return normal event size in bytes
     */
    public long getEventSize() {
        return sys.getEventSize();
    }


    /**
     * Gets the ET system's implementation language.
     * @return ET system's implementation language
     */
    public int getLanguage() {
        return sys.getLanguage();
    }


    /**
     * Gets the ET system's host name.
     * @return ET system's host name
     */
    public String getHost() {
        return sys.getHost();
    }


    /**
     * Gets the tcp server port number.
     * @return tcp server port number
     */
    public int getTcpPort() {
        return sys.getTcpPort();
    }


    /**
     * Gets all information about the ET system.
     *
     * @return object containing ET system information
     * @exception java.io.IOException
     *     if there are problems with network communication
     */
    synchronized public AllData getData() throws EtException, IOException {

        AllData data = new AllData();
        out.writeInt(Constants.netSysData);
        out.flush();

        // receive error
        int error = in.readInt();
        if (error != Constants.ok) {
            throw new EtException("error getting ET system data");
        }

        // receive incoming data

        // skip reading total size
        in.skipBytes(4);

        // read everything at once (4X faster that way),
        // put it into a byte array, and read from that
        // byte[] bytes = new byte[dataSize];
        // ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        // DataInputStream dis2 = new DataInputStream(bis);
        // in.readFully(bytes);

        // system data
        // data.sysData.read(dis2);
        data.sysData.read(in);
//System.out.println("getData:  read in system data");

        // station data
        int count = in.readInt();
//System.out.println("getData:  # of stations = " + count);
        data.statData = new StationData[count];
        for (int i=0; i < count; i++) {
            data.statData[i] = new StationData();
            data.statData[i].read(in);
        }
//System.out.println("getData:  read in station data");

        // attachment data
        count = in.readInt();
//System.out.println("getData:  # of attachments = " + count);
        data.attData = new AttachmentData[count];
        for (int i=0; i < count; i++) {
            data.attData[i] = new AttachmentData();
            data.attData[i].read(in);
        }
//System.out.println("getData:  read in attachment data");

        // process data
        count = in.readInt();
//System.out.println("getData:  # of processes = " + count);
        data.procData = new ProcessData[count];
        for (int i=0; i < count; i++) {
            data.procData[i] = new ProcessData();
            data.procData[i].read(in);
        }
//System.out.println("getData:  read in process data");

        return data;
    }


    /**
     * Gets histogram containing data showing how many events in GRAND_CENTRAL's
     * input list when new events are requested by users. This feature is not
     * available on Java ET systems.
     *
     * @return integer array containing histogram
     * @exception java.io.IOException
     *     if there are problems with network communication
     */
    synchronized public int[] getHistogram() throws IOException, EtException {
        byte[] data = new byte[4*(sys.getNumEvents()+1)];
        int[]  hist = new int[sys.getNumEvents()+1];

        out.writeInt(Constants.netSysHist);
        out.flush();

        // receive error code
        if (in.readInt() != Constants.ok) {
            throw new EtException("cannot get histogram");
        }

        in.readFully(data);
        for (int i=0; i < sys.getNumEvents()+1; i++) {
            hist[i] = Utils.bytesToInt(data, i*4);
        }
        return hist;
    }
}


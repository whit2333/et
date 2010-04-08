/*----------------------------------------------------------------------------*
 *  Copyright (c) 2001        Southeastern Universities Research Association, *
 *                            Thomas Jefferson National Accelerator Facility  *
 *                                                                            *
 *    This software was developed under a United States Government license    *
 *    described in the NOTICE file included as part of this distribution.     *
 *                                                                            *
 *    Author:  Carl Timmer                                                    *
 *             timmer@jlab.org                   Jefferson Lab, MS-12B3       *
 *             Phone: (757) 269-5130             12000 Jefferson Ave.         *
 *             Fax:   (757) 269-6248             Newport News, VA 23606       *
 *                                                                            *
 *----------------------------------------------------------------------------*/

package org.jlab.coda.et.system;

import java.lang.*;
import java.util.*;
import org.jlab.coda.et.exception.*;
import org.jlab.coda.et.system.AttachmentLocal;
import org.jlab.coda.et.Event;
import org.jlab.coda.et.Constants;
import org.jlab.coda.et.EventImpl;

/**
 * This class defines a linked list of events for use as either a station's
 * input or output list in a station.
 *
 * @author Carl Timmer
 */

class EventList {

  /** Linked list of events. */
  private LinkedList<EventImpl> events;

    /** Number of events put into this list. */
  private long eventsIn;
  /** Number of events taken out of this list. */
  private long eventsOut;

  // input list members only


    /** Number of events tried to put into this list when used with prescaling. */
  private long eventsTry;

  /** Flag telling the list to wake up the user waiting to read events. */
  private  boolean wakeAll;

  /** Count of the number of users sleeping on reading events. Last one to wake
   *  up needs to reset wakeAll. */
  private int waitingCount;

  // output list members only

  /** Position of the last high priority event in the linked list. */
  private int lastHigh;


    public LinkedList<EventImpl> getEvents() {
        return events;
    }
    public long getEventsTry() {
        return eventsTry;
    }
    public void setEventsTry(long eventsTry) {
        this.eventsTry = eventsTry;
    }
    public long getEventsIn() {
        return eventsIn;
    }
    public void setEventsIn(long eventsIn) {
        this.eventsIn = eventsIn;
    }


    public long getEventsOut() {
        return eventsOut;
    }

 

  /** Creates a new EventList object. */
  public EventList() {
    events = new LinkedList<EventImpl>();
  }

  // methods for waking up reading attachments on input lists

  /** Wake up an attachment waiting to read events from this list.
   *  @param att attachment to be woken up */
  public synchronized void wakeUp(AttachmentLocal att) {
    if (!att.isWaiting()) {
      return;
    }
    att.setWakeUp(true);
    notifyAll();
  }

  /** Wake up all attachments waiting to read events from this list. */
  public synchronized void wakeUpAll() {
    if (waitingCount < 1) {
      return;
    }
    wakeAll = true;
    notifyAll();
  }


  /**
   * Put all events into a station's input list as low priority. This is
   * synchronized and used in conductor threads and in the initial filling of
   * GRAND_CENTRAL station.
   * @param newEvents list of events to put
   */
  public void putInLow(List<EventImpl> newEvents) {
    // add all events to list's end
    events.addAll(newEvents);
    // keep stats
    eventsIn += newEvents.size();
  }

  /**
   * Synchronized version of putInLow for user to dump events into
   * GRAND_CENTRAL station.
   * @param newEvents array of events to put
   */
  public synchronized void putInGC(EventImpl[] newEvents) {
    // convert array to list and put as low priority events
    putInLow(Arrays.asList(newEvents));
  }

  /**
   * Synchronized version of putInLow for user to dump events into
   * GRAND_CENTRAL station.
   * @param newEvents list of events to put
   */
  public synchronized void putInGC(List<EventImpl> newEvents) {
    putInLow(newEvents);
  }

  /**
   * Put all events into the list regardless of how many are already in it.
   * Synchronized and used only in conductor threads to put events into a
   * station's input list. All high priority events are listed first in
   * newEvents.
   * @param newEvents list of events to put
   */
  public void putAll(List<EventImpl> newEvents) {
      // number of incoming events
      int num = newEvents.size();

      // all incoming events' priorities are low or no events in this EventList
      if ((events.size() == 0) || ((newEvents.get(0)).getPriority() == Constants.low))  {
          // adds new events to the end
          events.addAll(newEvents);
          /*
          if ((newEvents.get(0)).priority == Constants.low) {
            System.out.println("  putAll in as is as incoming are all low pri, " + newEvents.size());
          }
          if (events.size() == 0) {
            System.out.println("  putAll in as is as list EventList empty, " + newEvents.size());
          }
          */
      }

      // if any high pri events (count != 0) ...
      else {
          // find last high priority event already in list
          int highCount = 0;
          for (Event ev : events) {
              if (ev.getPriority() != Constants.high) {
                  break;
              }
              highCount++;
          }
//System.out.println("  putAll last high of input list = " + highCount);

          // add new high pri items
          int newHighCount = 0;
          for (EventImpl ev : newEvents) {
              if (ev.getPriority() != Constants.high) {
                  break;
              }
//System.out.println("  putAll add high " + ev.id + " at " + (highCount + newHighCount));
              events.add(highCount + newHighCount++, ev);
          }

          // rest are low pri, add to end
          if (newHighCount < num) {
//System.out.println("  putAll add " + (num - newHighCount) + " lows at end");
              events.addAll(newEvents.subList(newHighCount, num));
          }
      }
      // keep stats
      eventsIn += num;
      return;
  }

  /**
   * For user to put all events into station's output list. High & low
   * priorities may be mixed up in the newEvents list. May also be used to
   * restore events to the input list when user connection is broken.
   * @param newEvents array of events to put
   */
  public synchronized void put(EventImpl[] newEvents) {
      // if no events in list, initialize lastHigh
      if (events.size() == 0) {
	    lastHigh = 0;
      }

      // put events in one-by-one - with place depending on priority
      for (EventImpl ev : newEvents) {
        // if low priority event, add to the list end
        if (ev.getPriority() == Constants.low) {
//System.out.println(" put in low - " + ev.id);
	      events.addLast(ev);
        }
        // else if high pri event, add after other high priority events
        else {
//System.out.println(" put in high - " + ev.id);
          events.add(lastHigh++, ev);
        }
      }
      notify();
      return;
  }

  /**
   * For user to put all events into station's output list. High & low
   * priorities may be mixed up in the newEvents list. May also be used to
   * restore events to the input list when user connection is broken.
   * @param newEvents list of events to put
   */
  public synchronized void put(List<EventImpl> newEvents) {
      // if no events in list, initialize lastHigh
      if (events.size() == 0) {
	    lastHigh = 0;
      }

      // put events in one-by-one - with place depending on priority
      for (EventImpl ev : newEvents) {
        // if low priority event, add to the list end
        if (ev.getPriority() == Constants.low) {
//System.out.println(" put in low - " + ev.id);
	      events.addLast(ev);
        }
        // else if high pri event, add after other high priority events
        else {
//System.out.println(" put in high - " + ev.id);
          events.add(lastHigh++, ev);
        }
      }
      notify();
      return;
  }


    /**
     * For user to put all events into a station's input or output list. High & low
     * priorities may be mixed up in the newEvents list. Unlike {@link #put},
     * this method puts the new events BEFORE those already in the list.
     * Used to restore events to input/output lists when user connection is broken.
     * @param newEvents list of events to put
     */
    public synchronized void putReverse(List<EventImpl> newEvents) {
        // if no events in list, initialize lastHigh
        if (events.size() == 0) {
            lastHigh = 0;
        }
        else {
            // The lastHigh is NOT tracked for input lists, so let's do
            // it here since this method can be used for input lists.
            int highCount = 0;
            for (Event ev : events) {
                if (ev.getPriority() != Constants.high) {
                    break;
                }
                highCount++;
            }
            lastHigh = highCount;
        }

        // put events in one-by-one - with place depending on priority
        for (EventImpl ev : newEvents) {
          // if low priority event, add below last high priority but above low priority events
          if (ev.getPriority() == Constants.low) {
//System.out.println(" put in low - " + ev.id);
              events.add(lastHigh, ev);
          }
          // else if high pri event, add to the top
          else {
//System.out.println(" put in high - " + ev.id);
              events.add(0, ev);
              lastHigh++;
          }
        }
        notify();
        return;
    }


  /**
   * Used only by conductor to get all events from a station's output list.
   * @param eventsToGo list of event to get
   */
  public synchronized void get(List<EventImpl> eventsToGo) {
      eventsToGo.addAll(events);
      eventsOut += events.size();
      events.clear();
      return;
  }


  /**
   * For an attachment (in TcpServer thread) to get an array of events.
   * @param att attachment
   * @param mode wait mode
   * @param microSec time in microseconds to wait if timed wait mode
   * @param quantity number of events desired
   *
   * @exception EtEmptyException
   *     if the mode is asynchronous and the station's input list is empty
   * @exception EtTimeoutException
   *     if the mode is timed wait and the time has expired
   * @exception EtWakeUpException
   *     if the attachment has been commanded to wakeup,
   */
  public synchronized EventImpl[] get(AttachmentLocal att, int mode, int microSec, int quantity)
          throws EtEmptyException, EtWakeUpException, EtTimeoutException {

      int  nanos, count = events.size();
      long begin, microDelay, milliSec, elapsedTime = 0;

      // Sleep mode is never used since it is implemented in the TcpServer
      // thread by repeated calls in timed mode.
      if (count == 0) {
          if (mode == Constants.sleep) {
              while (count < 1) {
                  waitingCount++;
                  att.setWaiting(true);
//System.out.println("  get" + att.id + ": sleep");
                  try {
                      wait();
                  }
                  catch (InterruptedException ex) {
                  }

                  // if we've been told to wakeup & exit ...
                  if (att.isWakeUp() || wakeAll) {
                      att.setWakeUp(false);
                      att.setWaiting(false);
                      // last man to wake resets variable
                      if (--waitingCount < 1) {
                          wakeAll = false;
                      }
                      throw new EtWakeUpException("attachment " + att.getId() + " woken up");
                  }

                  att.setWaiting(false);
                  waitingCount--;
                  count = events.size();
              }
          }
          else if (mode == Constants.timed) {
              while (count < 1) {
                  microDelay = microSec - 1000*elapsedTime;
                  milliSec   = microDelay/1000L;
                  if (milliSec < 0) {
                      throw new EtTimeoutException("timed out");
                  }
                  nanos = 1000 * (int)(microDelay - 1000*milliSec);

                  waitingCount++;
                  att.setWaiting(true);
//System.out.println("  get" + att.getId() + ": wait " + milliSec + " ms and " +
//                   nanos + " nsec, elapsed time = " + elapsedTime);
                  begin = System.currentTimeMillis();
                  try {
                      wait(milliSec, nanos);
                  }
                  catch (InterruptedException ex) {
                  }
                  elapsedTime += System.currentTimeMillis() - begin;

                  // if we've been told to wakeup & exit ...
                  if (att.isWakeUp() || wakeAll) {
                      att.setWakeUp(false);
                      att.setWaiting(false);
                      // last man to wake resets variable
                      if (--waitingCount < 1) {
                          wakeAll = false;
                      }
                      throw new EtWakeUpException("attachment " + att.getId() + " woken up");
                  }

                  att.setWaiting(false);
                  waitingCount--;
                  count = events.size();
//System.out.println("  get" + att.id + ": woke up and counts = " + count);
              }
          }
          else if (mode == Constants.async) {
              throw new EtEmptyException("no events in list");
          }
      }

      if (quantity > count) {
          quantity = count;
      }
//System.out.println("  get"+ att.id + ": quantity = " + quantity);

      List<EventImpl> deleteList = events.subList(0, quantity);
      EventImpl[] eventsToGo = new EventImpl[quantity];
      deleteList.toArray(eventsToGo);
      deleteList.clear();

      eventsOut += quantity;
      return eventsToGo;
  }


    /**
     * For an attachment (in TcpServer thread) to get an array of events.
     * @param att attachment
     * @param mode wait mode
     * @param microSec time in microseconds to wait if timed wait mode
     * @param quantity number of events desired
     * @param group group number of events desired
     *
     * @exception EtEmptyException
     *     if the mode is asynchronous and the station's input list is empty
     * @exception EtTimeoutException
     *     if the mode is timed wait and the time has expired
     * @exception EtWakeUpException
     *     if the attachment has been commanded to wakeup,
     */
    public synchronized List<EventImpl> get(AttachmentLocal att, int mode, int microSec, int quantity, int group)
            throws EtEmptyException, EtWakeUpException, EtTimeoutException {

        int nanos, count = events.size(), groupCount = 0;
        EventImpl ev;
        boolean scanList = true;
        long begin, microDelay, milliSec, elapsedTime = 0;
        LinkedList<EventImpl> groupList = new LinkedList<EventImpl>();

        // Sleep mode is never used since it is implemented in the TcpServer
        // thread by repeated calls in timed mode.
        do {
            if (mode == Constants.sleep) {
                while (count < 1 || !scanList) {
                    waitingCount++;
                    att.setWaiting(true);
//System.out.println("  get" + att.id + ": sleep");
                    try {
                        wait();
                    }
                    catch (InterruptedException ex) {
                    }

                    // if we've been told to wakeup & exit ...
                    if (att.isWakeUp() || wakeAll) {
                        att.setWakeUp(false);
                        att.setWaiting(false);
                        // last man to wake resets variable
                        if (--waitingCount < 1) {
                            wakeAll = false;
                        }
                        throw new EtWakeUpException("attachment " + att.getId() + " woken up");
                    }

                    att.setWaiting(false);
                    waitingCount--;
                    count = events.size();
                    scanList = true;
                }
            }
            else if (mode == Constants.timed) {
                while (count < 1 || !scanList) {
                    microDelay = microSec - 1000*elapsedTime;
                    milliSec   = microDelay/1000L;
                    if (milliSec < 0) {
                        throw new EtTimeoutException("timed out");
                    }
                    nanos = 1000 * (int)(microDelay - 1000*milliSec);
//                    if (nanos > 999999) {
//                        System.out.println("nanos = " + nanos + ", millisec = " +
//                           milliSec + ", elapsed = " + elapsedTime + ", microSec = " + microSec + ", scanList = " + scanList);
//                  }

                    waitingCount++;
                    att.setWaiting(true);
//System.out.println("  get" + att.id + ": wait " + milliSec + " ms and " +
//                   nanos + " nsec, elapsed time = " + elapsedTime);
                    begin = System.currentTimeMillis();
                    try {
                        wait(milliSec, nanos);
                    }
                    catch (InterruptedException ex) {
                    }
                    elapsedTime += System.currentTimeMillis() - begin;

                    // if we've been told to wakeup & exit ...
                    if (att.isWakeUp() || wakeAll) {
                        att.setWakeUp(false);
                        att.setWaiting(false);
                        // last man to wake resets variable
                        if (--waitingCount < 1) {
                            wakeAll = false;
                        }
                        throw new EtWakeUpException("attachment " + att.getId() + " woken up");
                    }

                    att.setWaiting(false);
                    waitingCount--;
                    count = events.size();
//System.out.println("  get" + att.id + ": woke up and counts = " + count);
                    scanList = true;
                }
            }
            else if (mode == Constants.async) {
                throw new EtEmptyException("no events in list");
            }

            if (quantity > count) {
                quantity = count;
            }
//System.out.println("  get"+ att.id + ": quantity = " + quantity);

            for (ListIterator liter = events.listIterator(); liter.hasNext(); ) {
                ev = (EventImpl)liter.next();
                if (ev.getGroup() == group) {
                    groupList.add(ev);
                    if (++groupCount >= quantity)  break;
                }
            }

            scanList = false;

          // If we got nothing and we're Constants.sleep or Constants.timed, then try again
        } while (groupCount == 0 && mode != Constants.async);

        // remove from this list
        events.removeAll(groupList);
        eventsOut += groupList.size();
        return groupList;
    }
}

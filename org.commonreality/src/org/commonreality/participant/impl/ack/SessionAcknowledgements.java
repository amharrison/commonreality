package org.commonreality.participant.impl.ack;

/*
 * default logging
 */
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.IoServiceListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.commonreality.message.IMessage;
import org.commonreality.message.request.IAcknowledgement;

/**
 * contains session specific ack data. Many IMessages require an acknowledgment.
 * Some code may want to block until that ack is received. Once the ack is
 * received, any locks are released and the ack is moved to a memory sensitive
 * cache, allowing it to be accessed for a limited duration of time. <br/>
 * <br/>
 * 3/19/15 : recent profiling showed that the expireGarbageAcks() wasn't
 * behaving as expected. Specifically, we were holding on to acks that should
 * have expired. Specifically, the AckFutureReference's soft reference wasn't
 * reclaiming. Moved to weak references.
 * 
 * @author harrison
 */
public class SessionAcknowledgements
{

  /**
   * Logger definition
   */
  static private final transient Log                           LOGGER                  = LogFactory
                                                                                           .getLog(SessionAcknowledgements.class);

  private final IoSession                                      _session;

  /**
   * map, indexed by the IMessage's id of unacknowledged Acks. Once the ack is
   * received, the AckFuture gets moved to the memory sensitive cache.
   */
  private SortedMap<Long, CompletableFuture<IAcknowledgement>> _pendingAcknowledgments = Collections
                                                                                           .synchronizedSortedMap(new TreeMap<Long, CompletableFuture<IAcknowledgement>>());

  /**
   * this should be a capacity limited map..
   */

  // private SortedMap<Long, AckFutureReference> _acknowledged = Collections
  // .synchronizedSortedMap(new TreeMap<Long, AckFutureReference>());

  // .synchronizedSortedMap(new LRUMap());

  // private SortedSet<Long> _missingAcknowledgements = Collections
  // .synchronizedSortedSet(new TreeSet<Long>());

  // private int _maxMissingToRetain = 100;

  private IoServiceListener                                    _sessionListener;



  static public SessionAcknowledgements getSessionAcks(IoSession session)
  {
    return (SessionAcknowledgements) session
        .getAttribute(SessionAcknowledgements.class.getName());
  }

  public SessionAcknowledgements(IoSession session)
  {
    _session = session;

    _sessionListener = new IoServiceListener() {

      public void sessionDestroyed(IoSession arg0) throws Exception
      {
        // clean up.
        uninstall(arg0);
      }

      public void sessionCreated(IoSession arg0) throws Exception
      {
        // noop

      }

      public void serviceIdle(IoService arg0, IdleStatus arg1) throws Exception
      {
        // noop

      }

      public void serviceDeactivated(IoService arg0) throws Exception
      {
        // noop

      }

      public void serviceActivated(IoService arg0) throws Exception
      {
        // noop

      }
    };

    install(session);

  }

  protected void install(IoSession session)
  {
    session.setAttribute(SessionAcknowledgements.class.getName(), this);
    session.getService().addListener(_sessionListener);
    // add the filter for handling incoming responses
    session.getFilterChain().addLast("ackFilter", new AcknowledgmentIoFilter());
  }

  protected void uninstall(IoSession session)
  {
    session.getService().removeListener(_sessionListener);
    session.getFilterChain().remove("ackFilter");
    session.removeAttribute(SessionAcknowledgements.class.getName());
    _pendingAcknowledgments.clear();
    // _acknowledged.clear();
  }

  public CompletableFuture<IAcknowledgement> newAckFuture(IMessage message)
  {
    long id = message.getMessageId();
    // AckFuture future = new AckFuture(id);
    CompletableFuture<IAcknowledgement> future = new CompletableFuture<IAcknowledgement>();

    _pendingAcknowledgments.put(id, future);
    return future;
  }

  // public Future<IAcknowledgement> getAckFuture(long messageId)
  // {
  // AckFuture future = _pendingAcknowledgments.get(messageId);
  // if (future != null) return future;
  //
  // AckFutureReference reference = _acknowledged.get(messageId);
  // if (reference != null)
  // {
  // future = (AckFuture) reference.getFuture();
  // if (future != null) return future;
  //
  // if (LOGGER.isWarnEnabled())
  // LOGGER
  // .warn(String
  // .format(
  // "(%s) Ack for %d was received, but has since been reclaimed by GC. Returning empty ack",
  // getSession(), messageId));
  //
  // return new AckFuture(messageId, true);
  // }
  // else
  // {
  //
  // if (_missingAcknowledgements.contains(messageId))
  // if (LOGGER.isWarnEnabled())
  // LOGGER.warn(String.format(
  // "(%s) no acknowledgement for %d was ever receieved",
  // getSession(), messageId));
  //
  // if (LOGGER.isWarnEnabled())
  // {
  // long oldestPending = _pendingAcknowledgments.firstKey();
  // long youngestPending = _pendingAcknowledgments.lastKey();
  // long oldestAcked = _acknowledged.firstKey();
  // long youngestAcked = _acknowledged.lastKey();
  // LOGGER
  // .warn(String
  // .format(
  // "(%s) No record of messageId %d remains. pending[%d, %d] acknowledged[%d, %d]. Returning empty ack",
  // getSession(), messageId, oldestPending, youngestPending,
  // oldestAcked, youngestAcked));
  // }
  //
  // return new AckFuture(messageId, true);
  // }
  // }

  public void acknowledgementReceived(IAcknowledgement ack)
  {
    long requestId = ack.getRequestMessageId();
    if (LOGGER.isDebugEnabled())
      LOGGER.debug(String.format("(%s) Ack (%d) received for message (%d)",
          getSession(), ack.getMessageId(), requestId));

    /**
     * snag the future, update it, then move it to the _acknowledge set
     */
    CompletableFuture<IAcknowledgement> future = _pendingAcknowledgments
        .remove(requestId);
    if (future != null)
    // {
    // _acknowledged.put(requestId, new AckFutureReference(requestId, future,
    // true));
    // future.setAcknowledgement(ack);
    // assuming completablefuture
      future.complete(ack);
    // }

    /*
     * since this is session based, and we've received an ack, all earlier
     * requests were clearly not responded to, and will likely never arrive.
     */
    // synchronized (_pendingAcknowledgments)
    // {
    // SortedMap<Long, AckFuture> earlierFutures = _pendingAcknowledgments
    // .headMap(requestId);
    // Set<Long> missing = earlierFutures.keySet();
    //
    // if (missing.size() > 0)
    // {
    // _missingAcknowledgements.addAll(missing);
    //
    // if (LOGGER.isDebugEnabled())
    // LOGGER.debug(String.format(
    // "(%s) %d unacknowledged, older acks have been expired.",
    // getSession(), missing.size()));
    //
    // earlierFutures.clear();
    // }
    // }
    //
    // synchronized (_missingAcknowledgements)
    // {
    // while (_missingAcknowledgements.size() >= _maxMissingToRetain)
    // _missingAcknowledgements.remove(_missingAcknowledgements.first());
    // }
    //
    // /*
    // * now we zip through the acknowledged to clear out the expired
    // */
    // expireGarbageAcks();
  }

  /**
   * 
   */
  // private void expireGarbageAcks()
  // {
  // int expired = 0;
  // synchronized (_acknowledged)
  // {
  // int half = _acknowledged.size() / 2;
  // Iterator<Map.Entry<Long, AckFutureReference>> itr = _acknowledged
  // .entrySet().iterator();
  //
  // while (itr.hasNext() && half > 0)
  // {
  // half--;
  // Map.Entry<Long, AckFutureReference> entry = itr.next();
  // AckFutureReference reference = entry.getValue();
  // if (reference.getFuture() == null)
  // {
  // itr.remove();
  // if (LOGGER.isDebugEnabled())
  // LOGGER.debug(String.format(
  // "(%s) Ack for %d has been reclaimed by GC, removing",
  // getSession(), reference.getRequestId()));
  // expired++;
  // }
  // else
  // break;
  // }
  // }
  //
  // if (expired > 0)
  // if (LOGGER.isDebugEnabled())
  // LOGGER.debug(String.format("(%s) Expired %d ack futures", getSession(),
  // expired));
  // }

  public IoSession getSession()
  {
    return _session;
  }
}
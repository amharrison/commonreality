package org.commonreality.participant.impl.filters;

/*
 * default logging
 */
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Future;

import javolution.util.FastList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.commonreality.message.request.IAcknowledgement;
import org.commonreality.message.request.IRequest;
import org.commonreality.participant.impl.ack.AckFuture;
import org.commonreality.participant.impl.ack.AckFutureReference;

@Deprecated
public class AcknowledgmentIoFilter extends
    org.apache.mina.core.filterchain.IoFilterAdapter
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER       = LogFactory
                                                      .getLog(AcknowledgmentIoFilter.class);

  static private final String        REQUEST_LIST = AcknowledgmentIoFilter.class
                                                      .getName()
                                                      + ".requestList";

  static private final String        LAST_FUTURE  = AcknowledgmentIoFilter.class
                                                      .getName()
                                                      + ".lastFuture";

  static public final AckFuture      EMPTY        = new AckFuture(-1);

  static
  {
    EMPTY.setAcknowledgement(null);
  }

  /**
   * snag the acknowledgement future map, or create if it doesnt already exist
   * 
   * @param session
   * @return
   */
  @SuppressWarnings("unchecked")
  protected List<AckFutureReference> getAckMap(IoSession session)
  {
    List<AckFutureReference> ackMap = (List<AckFutureReference>) session
        .getAttribute(REQUEST_LIST);
    /**
     * null? create
     */
    if (ackMap == null)
    {
      ackMap = FastList.newInstance();

      session.setAttribute(REQUEST_LIST, ackMap);
    }
    return ackMap;
  }

  public Future<IAcknowledgement> getAcknowledgementFuture(IoSession session,
      IRequest request)
  {
    /**
     * first we check the last ack..
     */
    AckFuture ack = (AckFuture) session.getAttribute(LAST_FUTURE);

    if (ack != null && ack.getRequestMessageId() == request.getMessageId())
    {
      if (LOGGER.isDebugEnabled())
        LOGGER.debug(String.format(
            "Returning most recent ack future %d [session %s]",
            request.getMessageId(), session));

      return ack;
    }

    /*
     * nope, time for the long haul search. iterate through it, looking for the
     * corresponding id. Along the way, check the references, if any are null,
     * remove. If the ack is done, remove
     */
    long id = request.getMessageId();
    List<AckFutureReference> references = getAckMap(session);
    if (LOGGER.isDebugEnabled())
      LOGGER.debug(String.format(
          "ack future %d was not last one (%d), digging deeper (list: %d)",
          request.getMessageId(), ack != null ? ack.getRequestMessageId() : -1,
          references.hashCode()));

    synchronized (references)
    {
      ListIterator<AckFutureReference> itr = references.listIterator();
      while (itr.hasNext())
      {
        AckFutureReference ref = itr.next();
        ack = (AckFuture) ref.getFuture();
        long rId = ref.getRequestId();
        if (ack == null)
        {
          // reclaimed
          if (LOGGER.isDebugEnabled())
            LOGGER.debug(String.format("%d was gc'ed, removing", rId));

          itr.remove();
        }
        else
        {
          if (ack.hasBeenAcknowledged())
          {
            if (LOGGER.isDebugEnabled())
              LOGGER.debug(String.format("%d has been acknowledge, removing",
                  rId));

            itr.remove();
          }

          if (rId == id) return ack;
        }
      }
    }

    if (LOGGER.isWarnEnabled())
      LOGGER
          .warn(
              String
                  .format(
                      "Could not find future for %s.%d, returning empty ack. [session:%s, pending acks %d]",
                      request, request.getMessageId(), session,
                      references.size()), new RuntimeException("trace"));

    return EMPTY;
  }

  @Override
  public void sessionOpened(NextFilter nextFilter, IoSession session)
      throws Exception
  {
    getAckMap(session);

    super.sessionOpened(nextFilter, session);
  }

  @Override
  public void sessionClosed(NextFilter nextFilter, IoSession session)
      throws Exception
  {
    /*
     * ideally, we'd clean up, but since the session can be closed before the
     * getAck call is made, we need to keep this cruft around and let garbage
     * collection do its thing.
     */

    // FastList list = (FastList) getAckMap(session);
    // FastList.recycle(list);
    //
    // session.setAttribute(REQUEST_LIST, null);
    // session.setAttribute(LAST_FUTURE, null);
    super.sessionClosed(nextFilter, session);
  }

  @Override
  public void filterWrite(NextFilter nextFilter, IoSession session,
      WriteRequest writeRequest) throws Exception
  {
    Object message = writeRequest.getMessage();
    if (message instanceof IRequest)
    {
      IRequest request = (IRequest) message;
      AckFuture ack = new AckFuture(request.getMessageId());

      /*
       * since we are using weak references, this ensures that the most recent
       * request is held onto so that the participant can get the future<IAck>
       */
      session.setAttribute(LAST_FUTURE, ack);

      List<AckFutureReference> references = getAckMap(session);
      synchronized (references)
      {
        references.add(new AckFutureReference(request.getMessageId(), ack));
      }

      if (LOGGER.isDebugEnabled())
        LOGGER.debug(String.format(
            "Created new future ack for %d (%s) [session %s]",
            request.getMessageId(), request, session));
    }

    super.filterWrite(nextFilter, session, writeRequest);
  }

  @Override
  public void messageReceived(NextFilter nextFilter, IoSession session,
      Object message) throws Exception
  {
    if (message instanceof IAcknowledgement)
    {
      IAcknowledgement ackMsg = (IAcknowledgement) message;

      if (LOGGER.isDebugEnabled())
        LOGGER.debug(String.format("request %d acknowledged by %s ",
            ackMsg.getRequestMessageId(), ackMsg));

      /*
       * iterate through the messages awaiting acknowledgement, removing any
       * that have been GC'ed, or have been ack'ed and removing them. We will
       * leave this message's ack future until the next round
       */
      List<AckFutureReference> references = getAckMap(session);

      if (LOGGER.isDebugEnabled())
        LOGGER.debug(String.format(
            "Checking %s ack futures (list:%d) [session %s]",
            references.size(), references.hashCode(), session));

      long requestId = ackMsg.getRequestMessageId();
      // some stats to collect
      int reclaimed = 0;
      int done = 0;

      synchronized (references)
      {
        ListIterator<AckFutureReference> itr = references.listIterator();
        while (itr.hasNext())
        {
          AckFutureReference ref = itr.next();
          AckFuture ack = (AckFuture) ref.getFuture();

          if (ack == null)
          {
            // reclaimed
            if (LOGGER.isDebugEnabled())
              LOGGER.debug(String.format("%d was gc'ed, removing",
                  ref.getRequestId()));

            itr.remove();
            reclaimed++;
          }
          else if (ack.hasBeenAcknowledged())
          {
            if (LOGGER.isDebugEnabled())
              LOGGER.debug(String.format("%d has been acknowledge, removing",
                  ref.getRequestId()));

            itr.remove();
            done++;
          }
          else if (ref.getRequestId() == requestId)
          {
            if (LOGGER.isDebugEnabled())
              LOGGER.debug("Received acknowledgement of "
                  + ackMsg.getRequestMessageId() + " by " + ackMsg);

            ack.setAcknowledgement(ackMsg);
          }
          else if (ref.getRequestId() > requestId) break; // early exit
        }
      }

      if (LOGGER.isDebugEnabled())
        LOGGER.debug(String.format(
            "Future acknowledgments : %d GC'ed, %d done, %d remaining",
            reclaimed, done, references.size()));

    }

    super.messageReceived(nextFilter, session, message);
  }
}

package org.commonreality.participant.impl.ack;

/*
 * default logging
 */
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.session.IoSession;
import org.commonreality.message.request.IAcknowledgement;

public class AcknowledgmentIoFilter extends
    org.apache.mina.core.filterchain.IoFilterAdapter
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(AcknowledgmentIoFilter.class);

  @Override
  public void messageReceived(NextFilter nextFilter, IoSession session,
      Object message) throws Exception
  {

    if (message instanceof IAcknowledgement)
    {
      IAcknowledgement ackMsg = (IAcknowledgement) message;
      long requestId = ackMsg.getRequestMessageId();
      SessionAcknowledgements sessionAcks = SessionAcknowledgements
          .getSessionAcks(session);

      if (sessionAcks != null)
      {
        if (LOGGER.isDebugEnabled())
          LOGGER.debug(String.format("(%s) request %d acknowledged by %s ",
              session, requestId, ackMsg));

        sessionAcks.acknowledgementReceived(ackMsg);
      }

    }

    super.messageReceived(nextFilter, session, message);
  }
}

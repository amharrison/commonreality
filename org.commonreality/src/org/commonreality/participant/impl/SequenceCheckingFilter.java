package org.commonreality.participant.impl;

/*
 * default logging
 */
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.commonreality.message.IMessage;
import org.commonreality.message.command.time.ITimeCommand;
import org.commonreality.message.impl.BaseAcknowledgementMessage;
import org.commonreality.message.request.object.NewIdentifierAcknowledgement;
import org.commonreality.message.request.time.IRequestTime;
import org.commonreality.mina.filter.LogFilter;

public class SequenceCheckingFilter extends LogFilter
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(SequenceCheckingFilter.class);

  private long                       _lastSent;

  private long                       _lastReceived;

  private String                     _lastTypeSent;

  private String                     _lastTypeReceived;

  private String                     _name;

  public SequenceCheckingFilter(String name)
  {
    _name = name;
  }

  public void messageReceived(NextFilter nextFilter, IoSession session,
      Object message) throws Exception
  {
    if (message instanceof IMessage
        && !(message instanceof ITimeCommand
            || message instanceof BaseAcknowledgementMessage
            || message instanceof NewIdentifierAcknowledgement || message instanceof IRequestTime))
    {
      IMessage msg = (IMessage) message;
      if (_lastReceived > msg.getMessageId())
        LOGGER.error("[" + _name
            + "] Invalid reception order. New message has lower id ("
            + msg.getMessageId() + "." + msg.getClass().getSimpleName()
            + ") than previous (" + _lastReceived + "." + _lastTypeReceived
            + ") ", new RuntimeException());

      _lastReceived = msg.getMessageId();
      _lastTypeReceived = msg.getClass().getSimpleName();
    }
    super.messageReceived(nextFilter, session, message);
  }

  public void messageSent(NextFilter nextFilter, IoSession session,
      WriteRequest request) throws Exception
  {
    Object message = request.getMessage();
    if (message instanceof IMessage
        && !(message instanceof IRequestTime || message instanceof ITimeCommand
            || message instanceof BaseAcknowledgementMessage || message instanceof NewIdentifierAcknowledgement))
    {
      IMessage msg = (IMessage) message;
      if (_lastSent > msg.getMessageId())
        LOGGER.error("[" + _name
            + "] Invalid send order. New message has lower id ("
            + msg.getMessageId() + "." + msg.getClass().getSimpleName()
            + ") than previous (" + _lastSent + "." + _lastTypeSent + ") ",
            new RuntimeException());

      _lastSent = msg.getMessageId();
      _lastTypeSent = msg.getClass().getSimpleName();
    }
    super.messageSent(nextFilter, session, request);
  }
}

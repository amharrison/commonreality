/*
 * Created on Feb 23, 2007 Copyright (C) 2001-6, Anthony Harrison anh23@pitt.edu
 * (jactr.org) This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the License,
 * or (at your option) any later version. This library is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU Lesser General Public License for more details. You should have
 * received a copy of the GNU Lesser General Public License along with this
 * library; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.commonreality.participant.impl;

import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteToClosedSessionException;
import org.apache.mina.handler.demux.DemuxingIoHandler;
import org.apache.mina.handler.demux.ExceptionHandler;
import org.apache.mina.handler.demux.MessageHandler;
import org.commonreality.identifier.IIdentifier;
import org.commonreality.message.IMessage;
import org.commonreality.message.command.control.IControlCommand;
import org.commonreality.message.command.object.IObjectCommand;
import org.commonreality.message.command.object.IObjectData;
import org.commonreality.message.command.time.ITimeCommand;
import org.commonreality.message.notification.INotificationMessage;
import org.commonreality.message.request.connect.ConnectionRequest;
import org.commonreality.message.request.connect.IConnectionAcknowledgement;
import org.commonreality.message.request.object.INewIdentifierAcknowledgement;
import org.commonreality.participant.IParticipant.State;
import org.commonreality.participant.impl.ack.SessionAcknowledgements;
import org.commonreality.participant.impl.handlers.ConnectionHandler;
import org.commonreality.participant.impl.handlers.ControlHandler;
import org.commonreality.participant.impl.handlers.GeneralObjectHandler;
import org.commonreality.participant.impl.handlers.NewIdentifierHandler;
import org.commonreality.participant.impl.handlers.ObjectCommandHandler;
import org.commonreality.participant.impl.handlers.ObjectDataHandler;
import org.commonreality.participant.impl.handlers.TimeHandler;

;

/**
 * @author developer
 */
public class BasicParticipantIOHandler extends DemuxingIoHandler
{
  /**
   * logger definition
   */
  static private final Log     LOGGER = LogFactory
                                          .getLog(BasicParticipantIOHandler.class);

  private AbstractParticipant  _participant;

  private IoSession            _commonRealitySession;

  private IIdentifier.Type     _type;

  private GeneralObjectHandler _objectHandler;

  public BasicParticipantIOHandler(AbstractParticipant participant,
      IIdentifier.Type type)
  {
    _participant = participant;
    _type = type;

    /*
     * connection establishment
     */
    addReceivedMessageHandler(IConnectionAcknowledgement.class,
        new ConnectionHandler(_participant));

    /*
     * participant control
     */
    addReceivedMessageHandler(IControlCommand.class, new ControlHandler(
        _participant));

    /*
     * time management
     */
    addReceivedMessageHandler(ITimeCommand.class, new TimeHandler(_participant));

    /*
     * object management is handled by one class.
     */

    _objectHandler = new GeneralObjectHandler(_participant);

    addReceivedMessageHandler(IObjectCommand.class, new ObjectCommandHandler(
        _objectHandler));
    addReceivedMessageHandler(IObjectData.class, new ObjectDataHandler(
        _objectHandler));

    addReceivedMessageHandler(INewIdentifierAcknowledgement.class,
        new NewIdentifierHandler(_objectHandler));

    addReceivedMessageHandler(INotificationMessage.class,
        new MessageHandler<INotificationMessage>() {

          public void handleMessage(IoSession arg0, INotificationMessage arg1)
              throws Exception
          {
            _participant.getNotificationManager().post(arg1.getNotification());
          }

        });

    /*
     * general acknowledgement handler
     */
    addReceivedMessageHandler(IMessage.class, new MessageHandler<IMessage>() {

      public void handleMessage(IoSession arg0, IMessage arg1) throws Exception
      {
        // if (LOGGER.isDebugEnabled())
        // LOGGER.debug("Unhandled message received" + arg1);
      }
    });

    addSentMessageHandler(ConnectionRequest.class,
        new MessageHandler<ConnectionRequest>() {

          public void handleMessage(IoSession arg0, ConnectionRequest arg1)
              throws Exception
          {
            if (LOGGER.isDebugEnabled())
              LOGGER.debug("Sent connection request " + arg1);
          }
        });

    addSentMessageHandler(IMessage.class, new MessageHandler<IMessage>() {

      public void handleMessage(IoSession arg0, IMessage arg1) throws Exception
      {
        // if (LOGGER.isDebugEnabled()) LOGGER.debug("Sent " + arg1);
      }
    });

    addExceptionHandler(Throwable.class, new ExceptionHandler<Throwable>() {

      public void exceptionCaught(IoSession session, Throwable exception)
          throws Exception
      {
        /*
         * this can occur if we have pending writes but the connection has
         * already been closed from the other side, so we silently ignore it
         */
        if (exception instanceof WriteToClosedSessionException)
        {
          if (LOGGER.isDebugEnabled())
            LOGGER.debug("Tried to write to closed session ", exception);
          return;
        }

        /**
         * Error : error
         */
        LOGGER.error(
            "Exception caught from session " + session + ", closing. ",
            exception);

        if (!session.isClosing()) session.close();
      }

    });
  }

  /**
   * we need public access to this so that the participant can store its own
   * sent data, preventing the need for CR to send our own data right back to us
   * 
   * @return
   */
  public GeneralObjectHandler getObjectHandler()
  {
    return _objectHandler;
  }



  @Override
  public void sessionOpened(IoSession session) throws Exception
  {
    /*
     * add three different loggers, one at the head of the chain (last to write,
     * first to read), one before the "threadPool" , and one after the
     * threadpool
     */
    // session.getFilterChain().addAfter("threadPool", "postExec", new
    // SequenceCheckingFilter("postExecutor"));
    // session.getFilterChain().addBefore("threadPool", "preExec", new
    // SequenceCheckingFilter("preExecutor"));
    /*
     * we've opened a connection - for now, we'll assume that participants can
     * only connect to CR
     */

    /*
     * this will install itself. it attaches it's own service listener, that
     * will close and uninstall when the session is closed.
     */
    new SessionAcknowledgements(session);

    _commonRealitySession = session;

    super.sessionOpened(session);

    /*
     * great big fat hack, it is possible for the connection message to be sent
     * before CR has actually received the sessionOpened callback, in which case
     * the message will be dropped. so we delay the sending of this message.
     * grrrr... I suspect this is actually a mina bug as it should probably
     * queue all messages received and deliver them after sessionOpen
     */
    AbstractParticipant.getPeriodicExecutor().schedule(new Runnable() {
      public void run()
      {
        _participant.send(new ConnectionRequest(_participant.getName(), _type,
            _participant.getCredentials(), _participant
                .getAddressingInformation()));
        if (LOGGER.isDebugEnabled()) LOGGER.debug("Sent connection request");
      }
    }, 50, TimeUnit.MILLISECONDS);

    if (LOGGER.isDebugEnabled()) LOGGER.debug("Session opened w/ CR");
  }

  @Override
  public void sessionClosed(IoSession session) throws Exception
  {
    if (session == _commonRealitySession)
    {
      if (LOGGER.isDebugEnabled())
        LOGGER.debug("Session with common reality closed");
      _commonRealitySession = null;

      if (_participant.stateMatches(State.STARTED, State.SUSPENDED))
        _participant.stop();

      if (_participant.stateMatches(State.CONNECTED, State.INITIALIZED,
          State.UNKNOWN, State.STOPPED))
        _participant.shutdown();
    }
    super.sessionClosed(session);
  }

  public IoSession getCommonRealitySession()
  {
    return _commonRealitySession;
  }
}

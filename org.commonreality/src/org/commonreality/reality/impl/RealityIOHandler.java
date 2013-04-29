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
package org.commonreality.reality.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteToClosedSessionException;
import org.apache.mina.handler.demux.DemuxingIoHandler;
import org.apache.mina.handler.demux.ExceptionHandler;
import org.apache.mina.handler.demux.MessageHandler;
import org.commonreality.identifier.IIdentifier;
import org.commonreality.message.IMessage;
import org.commonreality.message.notification.INotificationMessage;
import org.commonreality.message.request.connect.IConnectionRequest;
import org.commonreality.message.request.object.INewIdentifierRequest;
import org.commonreality.message.request.object.IObjectCommandRequest;
import org.commonreality.message.request.object.IObjectDataRequest;
import org.commonreality.message.request.time.IRequestTime;
import org.commonreality.participant.impl.ack.SessionAcknowledgements;
import org.commonreality.participant.impl.handlers.GeneralObjectHandler;
import org.commonreality.reality.impl.handler.ConnectionHandler;
import org.commonreality.reality.impl.handler.NewIdentifierHandler;
import org.commonreality.reality.impl.handler.ObjectCommandHandler;
import org.commonreality.reality.impl.handler.ObjectDataHandler;
import org.commonreality.reality.impl.handler.TimeHandler;

/**
 * @author developer
 */
public class RealityIOHandler extends DemuxingIoHandler
{
  /**
   * logger definition
   */
  static private final Log          LOGGER = LogFactory
                                               .getLog(RealityIOHandler.class);

  private DefaultReality            _reality;

  private StateAndConnectionManager _manager;

  @SuppressWarnings("unchecked")
  public RealityIOHandler(DefaultReality reality)
  {
    _reality = reality;

    _manager = new StateAndConnectionManager(reality, reality
        .getCentralExector());
    /*
     * for now we are wide open...
     */
    _manager.setPromiscuous(true);

    /*
     * handle connection requests
     */
    addReceivedMessageHandler(IConnectionRequest.class, new ConnectionHandler(
        _manager));

    addReceivedMessageHandler(IRequestTime.class, new TimeHandler(_reality));

    addReceivedMessageHandler(INotificationMessage.class,
        new MessageHandler<INotificationMessage>() {

          public void handleMessage(IoSession arg0, INotificationMessage arg1)
              throws Exception
          {
            // reroute immediately
            IIdentifier destination = arg1.getDestination();
            _reality.send(destination, arg1);
          }
        });

    /*
     * object management
     */
    GeneralObjectHandler oh = new GeneralObjectHandler(_reality);

    addReceivedMessageHandler(INewIdentifierRequest.class,
        new NewIdentifierHandler(_reality, _manager, oh));

    addReceivedMessageHandler(IObjectCommandRequest.class,
        new ObjectCommandHandler(_reality, _manager, oh));

    addReceivedMessageHandler(IObjectDataRequest.class, new ObjectDataHandler(
        _reality, _manager, oh));

    /*
     * general acknowledgement handler
     */
    addReceivedMessageHandler(IMessage.class, new MessageHandler<IMessage>() {

      public void handleMessage(IoSession arg0, IMessage arg1) throws Exception
      {
//        if (LOGGER.isDebugEnabled())
//          LOGGER.debug("Unhandled message received" + arg1);
      }

    });

    addSentMessageHandler(IMessage.class, new MessageHandler<IMessage>() {

      public void handleMessage(IoSession arg0, IMessage arg1) throws Exception
      {
//        if (LOGGER.isDebugEnabled()) LOGGER.debug("Sent " + arg1);
      }
    });

    addExceptionHandler(Throwable.class, new ExceptionHandler<Throwable>() {

      public void exceptionCaught(IoSession session, Throwable exception)
          throws Exception
      {
        /*
         * this can occur if we have pending writes but the connection has already
         * been closed from the other side, so we silently ignore it
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

  public StateAndConnectionManager getManager()
  {
    return _manager;
  }



  @Override
  public void sessionOpened(IoSession session) throws Exception
  {
    /**
     * will install itself
     */
    new SessionAcknowledgements(session);

    super.sessionOpened(session);
    
    if (LOGGER.isDebugEnabled()) LOGGER.debug("Session opened " + session);
  }

  @Override
  public void sessionClosed(IoSession session) throws Exception
  {
    IIdentifier identifier = _manager.participantDisconnected(session);

    if (LOGGER.isDebugEnabled())
      LOGGER.debug("Closed connection with " + identifier);

    /*
     * clean up this identifier's objects.. and notify everyone else
     */
    super.sessionClosed(session);
  }

}

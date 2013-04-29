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
package org.commonreality.reality.impl.handler;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.handler.demux.MessageHandler;
import org.commonreality.message.request.connect.IConnectionRequest;
import org.commonreality.reality.impl.StateAndConnectionManager;

/**
 * @author developer
 */
public class ConnectionHandler implements MessageHandler<IConnectionRequest>
{

  private StateAndConnectionManager _manager;

  public ConnectionHandler(StateAndConnectionManager manager)
  {
    _manager = manager;
  }

//  /**
//   * @see org.apache.mina.handler.demux.MessageHandler#messageReceived(org.apache.mina.common.IoSession,
//   *      java.lang.Object)
//   */
//  public void messageReceived(IoSession session, Object message)
//      throws Exception
//  {
//    IConnectionRequest request = (IConnectionRequest) message;
//    IIdentifier identifier = null;
//    ICredentials credentials = null;
//    try
//    {
//      if (LOGGER.isDebugEnabled())
//        LOGGER.debug("connection opened, checking " + request);
//      credentials = request.getCredentials();
//
//      identifier = _reality.getConnectionTracker().acceptConnection(
//          credentials, session, request.getAddressingInformation(),
//          request.getSource());
//
//      if (LOGGER.isDebugEnabled())
//        LOGGER.debug("Connection granted, assigning " + identifier);
//
//      /*
//       * everything is aok.. reply
//       */
//      session.write(new ConnectionAcknowledgment(_reality.getIdentifier(),
//          request.getMessageId(), "Granted", identifier));
//
//      if (LOGGER.isDebugEnabled())
//        LOGGER.debug("Sending current agent/sensor data");
//
//      /*
//       * pass along all we know about the current participants. just before we
//       * send the data we do something a little crazy. we attach a listener to
//       * the object manager, then we snag all the data and send it out. after we
//       * have authorized the connection (which at this point and time, we know
//       * we will) we remove the listeners. This prevents the odd threading issue
//       * that can arise when two participants connect at roughly the sametime.
//       * The firs participant A, might be authorized, but not yet have sent its
//       * data when B finishes receiving the pre-existing participant data from
//       * below. If the data for A arrives after the data has been sent, B will
//       * be completely ignorant of A.. Even though at this point B might be
//       * sending data to us, because mina processes messages FIFO for an
//       * IoSession, we wont worry about getting ourselves
//       */
//
//      ISensorListener tmpSensorListener = (ISensorListener) sendObjectInformation(
//          _reality.getSensorObjectManager(), session, identifier);
//
//      IAgentListener tmpAgentListener = (IAgentListener) sendObjectInformation(
//          _reality.getAgentObjectManager(), session, identifier);
//
//      /*
//       * 
//       * They will be accepted, but tell then to initialize first. If we initialized
//       * after accepting, it would be possible for another thread to call IReality.start()
//       * between the acceptance and initialization. The new participant would
//       * then be asked to start before initialization which is not correct. At least 
//       * this way, the new participant will get the initialization request, but perhaps
//       * not the start. We still need to deal with the proper handling of states
//       * when connecting to an active reality.. However, we currently make sure everyone
//       * has fully connected before sending start commands, so this shouldn't be an
//       * issue. When we go to truly distributed runs (participants on multiple machines),
//       * this will need to be resolved
//       */
//      if (LOGGER.isDebugEnabled())
//        LOGGER.debug("Asking " + identifier + " to initialize");
//      session.write(new ControlCommand(_reality.getIdentifier(),
//          IControlCommand.State.INITIALIZE));
//      
//      /*
//       * officially authorize, in that we will track them and potentially add
//       * them as a clock owner
//       */
//      _reality.getConnectionTracker().authorizeConnection(identifier,
//          credentials);
//      
//
//      /*
//       * now we can remove the listeners
//       */
//      _reality.getSensorObjectManager().removeListener(tmpSensorListener);
//      _reality.getAgentObjectManager().removeListener(tmpAgentListener);
//
//    }
//    catch (Exception se)
//    {
//      if (LOGGER.isWarnEnabled())
//        LOGGER.warn("Not accepting connection from " + session, se);
//
//      _reality.getConnectionTracker().rejectConnection(identifier, credentials);
//      /*
//       * no good, send ack and then disconnect.
//       */
//      session.write(
//          new ConnectionAcknowledgment(_reality.getIdentifier(), request
//              .getMessageId(), se.getMessage())).join();
//      session.close();
//    }
//  }

  public void handleMessage(IoSession session, IConnectionRequest request)
      throws Exception
  {
    _manager.participantConnected(session, request);
  }

//  @SuppressWarnings("unchecked")
//  protected IObjectListener sendObjectInformation(IObjectManager objectManager,
//      final IoSession session, final IIdentifier participantId)
//  {
//
//    final Collection<IIdentifier> identifiers = Collections
//        .synchronizedCollection(new HashSet<IIdentifier>());
//
//    IObjectListener listener = null;
//
//    if (objectManager instanceof SensorObjectManager)
//      listener = new ISensorListener() {
//
//        public void objectsAdded(IObjectEvent<ISensorObject, ?> addEvent)
//        {
//          for (ISimulationObject object : addEvent.getObjects())
//          {
//            IIdentifier identifier = object.getIdentifier();
//            if (!identifiers.contains(identifier))
//            {
//              if (LOGGER.isWarnEnabled())
//                LOGGER.warn("addition of " + identifier
//                    + " snuck in while adding " + participantId
//                    + ", routing message to " + participantId);
//              /*
//               * a new sensor, other than the sent data
//               */
//              session.write(new ObjectData(_reality.getIdentifier(),
//                  Collections.singleton((IObjectDelta) new FullObjectDelta(
//                      object))));
//              session
//                  .write(new ObjectCommand(_reality.getIdentifier(),
//                      IObjectCommand.Type.ADDED, Collections
//                          .singleton(identifier)));
//            }
//          }
//        }
//
//        public void objectsRemoved(IObjectEvent<ISensorObject, ?> removeEvent)
//        {
//          for (ISimulationObject object : removeEvent.getObjects())
//          {
//            IIdentifier identifier = object.getIdentifier();
//            if (!identifiers.contains(identifier))
//            {
//              if (LOGGER.isWarnEnabled())
//                LOGGER.warn("removal of " + identifier
//                    + " snuck in while adding " + participantId
//                    + ", routing message to " + participantId);
//              /*
//               * a removed sensor, other than the sent data
//               */
//              session.write(new ObjectCommand(_reality.getIdentifier(),
//                  IObjectCommand.Type.REMOVED, Collections
//                      .singleton(identifier)));
//            }
//          }
//
//        }
//
//        public void objectsUpdated(IObjectEvent<ISensorObject, ?> updateEvent)
//        {
//
//          for (IObjectDelta delta : updateEvent.getDeltas())
//          {
//            IIdentifier identifier = delta.getIdentifier();
//            if (!identifiers.contains(identifier))
//            {
//              if (LOGGER.isWarnEnabled())
//                LOGGER.warn("Update of " + identifier
//                    + " snuck in while adding " + participantId
//                    + ", routing message to " + participantId);
//              /*
//               * a new sensor, other than the sent data
//               */
//              session.write(new ObjectData(_reality.getIdentifier(),
//                  Collections.singleton(delta)));
//              session.write(new ObjectCommand(_reality.getIdentifier(),
//                  IObjectCommand.Type.UPDATED, Collections
//                      .singleton(identifier)));
//            }
//          }
//
//        }
//      };
//    else
//      listener = new IAgentListener() {
//
//        public void objectsAdded(IObjectEvent<IAgentObject, ?> addEvent)
//        {
//          for (ISimulationObject object : addEvent.getObjects())
//          {
//            IIdentifier identifier = object.getIdentifier();
//            if (!identifiers.contains(identifier))
//            {
//              if (LOGGER.isWarnEnabled())
//                LOGGER.warn("addition of " + identifier
//                    + " snuck in while adding " + participantId
//                    + ",  routing message to " + participantId);
//              /*
//               * a new sensor, other than the sent data
//               */
//              session.write(new ObjectData(_reality.getIdentifier(),
//                  Collections.singleton((IObjectDelta) new FullObjectDelta(
//                      object))));
//              session
//                  .write(new ObjectCommand(_reality.getIdentifier(),
//                      IObjectCommand.Type.ADDED, Collections
//                          .singleton(identifier)));
//            }
//          }
//
//        }
//
//        public void objectsRemoved(IObjectEvent<IAgentObject, ?> removeEvent)
//        {
//          for (ISimulationObject object : removeEvent.getObjects())
//          {
//            IIdentifier identifier = object.getIdentifier();
//            if (!identifiers.contains(identifier))
//            {
//              if (LOGGER.isWarnEnabled())
//                LOGGER.warn("removal of " + identifier
//                    + " snuck in while adding " + participantId
//                    + ", routing message to " + participantId);
//              /*
//               * a removed sensor, other than the sent data
//               */
//              session.write(new ObjectCommand(_reality.getIdentifier(),
//                  IObjectCommand.Type.REMOVED, Collections
//                      .singleton(identifier)));
//            }
//          }
//
//        }
//
//        public void objectsUpdated(IObjectEvent<IAgentObject, ?> updateEvent)
//        {
//          for (IObjectDelta delta : updateEvent.getDeltas())
//          {
//            IIdentifier identifier = delta.getIdentifier();
//            if (!identifiers.contains(identifier))
//            {
//              if (LOGGER.isWarnEnabled())
//                LOGGER.warn("Update of " + identifier
//                    + " snuck in while adding " + participantId
//                    + ", routing message to " + participantId);
//              /*
//               * a new sensor, other than the sent data
//               */
//              session.write(new ObjectData(_reality.getIdentifier(),
//                  Collections.singleton(delta)));
//              session.write(new ObjectCommand(_reality.getIdentifier(),
//                  IObjectCommand.Type.UPDATED, Collections
//                      .singleton(identifier)));
//            }
//          }
//
//        }
//      };
//
//    /*
//     * this fix is not completely correct. An add might still arrive between the
//     * addListener() and getIdentifiers() calls, which would result in the
//     * listener sending off the relavent event followed by the add. Things would
//     * get really strange if one of those events that the listener caught was an
//     * update or a remove.. however, those should not occur as update and remove
//     * of participants is very rare. The actual concern is, again, a double add.
//     * But at least the AbstractObjectHandler will announce that occurence loud
//     * and proud
//     */
//    objectManager.addListener(listener, InlineExecutor.get());
//    identifiers.addAll(objectManager.getIdentifiers());
//
//    Collection<IObjectDelta> data = new ArrayList<IObjectDelta>();
//
//    for (IIdentifier id : identifiers)
//      data.add(new FullObjectDelta(objectManager.get(id)));
//
//    if (data.size() != 0)
//    {
//      session.write(new ObjectData(_reality.getIdentifier(), data));
//      session.write(new ObjectCommand(_reality.getIdentifier(),
//          IObjectCommand.Type.ADDED, identifiers));
//    }
//
//    return listener;
//  }
}

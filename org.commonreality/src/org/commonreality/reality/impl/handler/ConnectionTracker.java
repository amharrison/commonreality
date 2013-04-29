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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.session.IoSession;
import org.commonreality.identifier.IIdentifier;
import org.commonreality.message.credentials.ICredentials;
import org.commonreality.participant.addressing.IAddressingInformation;
import org.commonreality.reality.IReality;
import org.commonreality.reality.impl.StateAndConnectionManager;
import org.commonreality.time.impl.MasterClock;

/**
 * class that tracks which connection credentials we will accept as well as
 * assigns, links and accesses IParticipantIdentifiers and
 * IAddressingInformation
 * </br>
 * </br>
 * This is now deprecated in favor of {@link StateAndConnectionManager}
 * 
 * @author developer
 */
@Deprecated
public class ConnectionTracker
{
  /**
   * logger definition
   */
  static private final Log                         LOGGER                 = LogFactory
                                                                              .getLog(ConnectionTracker.class);

  static private final String                      CREDENTIALS            = ConnectionTracker.class
                                                                              .getName()
                                                                              + ".credentials";

  static private final String                      IDENTIFIER             = ConnectionTracker.class
                                                                              .getName()
                                                                              + ".identifier";

  private boolean                                  _promiscuous           = true;

  private Set<ICredentials>                        _validCredentials;

  private Map<ICredentials, IoSession>             _acceptedConnections;

  private Map<ICredentials, IoSession>             _pendingConnections;

  private Map<IIdentifier, IAddressingInformation> _addressInfo;

  private Map<IIdentifier, IAddressingInformation> _pendingAddressInfo;

  private Map<IIdentifier, IoSession>              _sessionMap;

  private Map<IIdentifier, IoSession>              _pendingSessionMap;

  private IReality                                 _reality;

  private ICredentials                             _clockOwnerCredentials = null;

  public ConnectionTracker(IReality reality)
  {
    _reality = reality;
    _validCredentials = new HashSet<ICredentials>();
    _acceptedConnections = new HashMap<ICredentials, IoSession>();
    _addressInfo = new HashMap<IIdentifier, IAddressingInformation>();
    _sessionMap = new HashMap<IIdentifier, IoSession>();
    _pendingAddressInfo = new HashMap<IIdentifier, IAddressingInformation>();
    _pendingConnections = new HashMap<ICredentials, IoSession>();
    _pendingSessionMap = new HashMap<IIdentifier, IoSession>();
  }

  public IReality getReality()
  {
    return _reality;
  }

  synchronized public void grantCredentials(ICredentials credentials,
      boolean wantsClockOwnership)
  {
    if (!_validCredentials.contains(credentials))
      _validCredentials.add(credentials);
    else if (LOGGER.isDebugEnabled())
      LOGGER.debug("Credentials are already recognized");

    if (wantsClockOwnership) _clockOwnerCredentials = credentials;
  }

  synchronized public void revokeCredentials(ICredentials credentials)
  {
    _validCredentials.remove(credentials);
    IoSession session = _acceptedConnections.get(credentials);
    if (session != null)
    {
      if (LOGGER.isDebugEnabled())
        LOGGER.debug("Session for revoked credentials is active, closing");
      session.close();
    }
  }

  synchronized public IIdentifier connectionClosed(IoSession session)
  {
    IIdentifier identifier = (IIdentifier) session.getAttribute(IDENTIFIER);

    _addressInfo.remove(identifier);
    _sessionMap.remove(identifier);

    ICredentials credentials = (ICredentials) session.getAttribute(CREDENTIALS);
    if (_acceptedConnections.containsKey(credentials))
      _acceptedConnections.put(credentials, null);

    if (_clockOwnerCredentials == null)
      _reality.getClock().removeOwner(identifier);
    else if (_clockOwnerCredentials.equals(credentials))
    {
      MasterClock clock = _reality.getClock();
      clock.removeOwner(identifier);
      _clockOwnerCredentials = null;
      /*
       * here's a sticky situation, if there are still participants, they are
       * now without a clock owner.. so, we add everyone and then force the
       * clock.
       */
      for (IIdentifier id : _sessionMap.keySet())
        clock.addOwner(id);

      clock.setTime(clock.getTime());
    }

    return identifier;
  }

  synchronized public void validateAddressing(IoSession session,
      IAddressingInformation address)
  {

  }
  
  synchronized public void rejectConnection(IIdentifier identifier, ICredentials credentials)
  {
    _pendingAddressInfo.remove(identifier);
    _pendingConnections.remove(credentials);
    _pendingSessionMap.remove(identifier);
  }

  synchronized public void authorizeConnection(IIdentifier identifier, ICredentials credentials)
  {
    _addressInfo.put(identifier, _pendingAddressInfo.remove(identifier));
    _acceptedConnections.put(credentials, _pendingConnections.remove(identifier));
    _sessionMap.put(identifier, _pendingSessionMap.remove(identifier));
    
    /**
     * if there is no clock owner specified, everyone shares in the
     * ownership. if there is a clock owner, it is set.
     */
    if (_clockOwnerCredentials == null
        || _clockOwnerCredentials.equals(credentials))
      _reality.getClock().addOwner(identifier);
  }

  /**
   * check to see if the connection should be accepted based on the credentials.
   * If it is to be accepted, the connection will be accepted provisionally and
   * an {@link IIdentifier} will be assigned to the participant. The connection
   * is not officially connected, however, until {@link #authorizeConnection(IIdentifier, ICredentials)}
   * is called.
   * 
   * @param credentials
   * @param session
   * @param addressInfo
   * @param template
   * @return
   */
  synchronized public IIdentifier acceptConnection(ICredentials credentials,
      IoSession session, IAddressingInformation addressInfo,
      IIdentifier template)
  {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("Connection request from " + session);

    if (_validCredentials.contains(credentials) || _promiscuous)
    {
      if (LOGGER.isDebugEnabled()) LOGGER.debug("Credentials passed");

      if (_acceptedConnections.get(credentials) == null)
      {
        if (LOGGER.isDebugEnabled()) LOGGER.debug("Not already connected");

        validateAddressing(session, addressInfo);

        IIdentifier identifier = _reality.newIdentifier(_reality
            .getIdentifier(), template);

        session.setAttribute(CREDENTIALS, credentials);
        session.setAttribute(IDENTIFIER, identifier);

        _pendingAddressInfo.put(identifier, addressInfo);
        _pendingConnections.put(credentials, session);
        _pendingSessionMap.put(identifier, session);

        if (LOGGER.isDebugEnabled())
        {
          LOGGER.debug("We can accept the connection from " + session
              + " with " + credentials);
          LOGGER.debug("Granting " + identifier);
        }

        return identifier;
      }

      throw new SecurityException(
          "Cannot accept connection because credentials are already in use");
    }
    throw new SecurityException(
        "Cannot accept connection because credentials are invalid");
  }

  synchronized public IAddressingInformation getAddressingInformation(
      IIdentifier identifier)
  {
    return _addressInfo.get(identifier);
  }

  synchronized public Collection<IoSession> getActiveConnections()
  {
    return new ArrayList<IoSession>(_sessionMap.values());
  }

  synchronized public IoSession getSession(IIdentifier identifier)
  {
    return _sessionMap.get(identifier);
  }

  static public IIdentifier getIdentifier(IoSession session)
  {
    return (IIdentifier) session.getAttribute(IDENTIFIER);
  }
}

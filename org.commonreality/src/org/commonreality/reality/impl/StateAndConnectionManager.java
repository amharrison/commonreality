package org.commonreality.reality.impl;

/*
 * default logging
 */
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.session.IoSession;
import org.commonreality.efferent.IEfferentCommand;
import org.commonreality.identifier.IIdentifier;
import org.commonreality.message.command.control.ControlCommand;
import org.commonreality.message.command.control.IControlAcknowledgement;
import org.commonreality.message.command.control.IControlCommand;
import org.commonreality.message.command.object.IObjectCommand;
import org.commonreality.message.command.object.ObjectCommand;
import org.commonreality.message.command.object.ObjectData;
import org.commonreality.message.credentials.ICredentials;
import org.commonreality.message.request.IAcknowledgement;
import org.commonreality.message.request.connect.ConnectionAcknowledgment;
import org.commonreality.message.request.connect.IConnectionRequest;
import org.commonreality.object.IAfferentObject;
import org.commonreality.object.IEfferentObject;
import org.commonreality.object.IRealObject;
import org.commonreality.object.ISimulationObject;
import org.commonreality.object.delta.FullObjectDelta;
import org.commonreality.object.delta.IObjectDelta;
import org.commonreality.object.identifier.ISensoryIdentifier;
import org.commonreality.object.manager.IMutableObjectManager;
import org.commonreality.object.manager.IObjectManager;
import org.commonreality.participant.IParticipant;
import org.commonreality.participant.IParticipant.State;
import org.commonreality.participant.addressing.IAddressingInformation;
import org.commonreality.reality.IReality;
import org.commonreality.reality.impl.handler.ObjectCommandHandler;
import org.commonreality.time.IClock;
import org.commonreality.time.impl.OwnedClock.OwnedAuthoritativeClock;

/**
 * state and connection manager does just that but is centralized so that we can
 * ensure synchronization during the connection and state change phases of a
 * simulation's life. </br> </br> previous versions that used a separate
 * connection manager and state handler was not sufficient as state changes
 * could occur while a connection was being established. Even more
 * frustratingly, if two connections were established at the same time, one or
 * the other might not get the data for the other. </br>
 * 
 * @author harrison
 */
public class StateAndConnectionManager
{
  /**
   * Logger definition
   */
  static private final transient Log     LOGGER                  = LogFactory
                                                                     .getLog(StateAndConnectionManager.class);

  static private final String            CREDENTIALS             = StateAndConnectionManager.class
                                                                     .getName()
                                                                     + ".credentials";

  static private final String            IDENTIFIER              = StateAndConnectionManager.class
                                                                     .getName()
                                                                     + ".identifier";

  private TrackedReadWriteLock           _lock                   = new TrackedReadWriteLock();

  private IReality                       _reality;

  private long                           _acknowledgementTimeout = 2000;

  /**
   * sessions keyed by id
   */
  private Map<IIdentifier, IoSession>    _activeParticipantSessions;

  private Map<IIdentifier, IoSession>    _pendingParticipantSessions;

  /**
   * id keyed by credentials
   */
  private Map<ICredentials, IIdentifier> _activeParticipantCredentials;

  /**
   * credentials of participants that will be allowed to participant if we
   * aren't running promiscuous
   */
  private Set<ICredentials>              _validCredentials;

  /**
   * credentails of participants that have been connected but not yet accepted
   */
  private Set<ICredentials>              _pendingCredentials;

  private boolean                        _isPromiscuous          = false;

  /**
   * the credentials of the unique clock owner, if there is one. otherwise all
   * participants share in the ownership
   */
  private ICredentials                   _clockOwnerCredentials;

  /**
   * this is the executor that will monitor the control acknowledgements during
   * the participant connection sequence. We can't do this from within the
   * connected,accepted methods since they are called on the io processing
   * thread for that participant (i.e. it can never get the acknowledgement
   */
  private Executor                       _centralExecutor;

  public StateAndConnectionManager(IReality reality, Executor executor)
  {
    _reality = reality;
    _activeParticipantSessions = new HashMap<IIdentifier, IoSession>();
    _pendingParticipantSessions = new HashMap<IIdentifier, IoSession>();
    _activeParticipantCredentials = new HashMap<ICredentials, IIdentifier>();
    _validCredentials = new HashSet<ICredentials>();
    _pendingCredentials = new HashSet<ICredentials>();
    _centralExecutor = executor;
  }

  public ReentrantReadWriteLock getStateLock()
  {
    return _lock;
  }

  public void setAcknowledgementTimeout(long timeout)
  {
    _acknowledgementTimeout = timeout;
  }

  public long getAcknowledgementTimeout()
  {
    return _acknowledgementTimeout;
  }

  /**
   * if promiscuous is true, all participants will be allowed to connect
   * regardless of their credentials.
   * 
   * @param promiscuous
   */
  public void setPromiscuous(boolean promiscuous)
  {
    try
    {
      _lock.writeLock().lock();
      _isPromiscuous = promiscuous;
    }
    finally
    {
      _lock.writeLock().unlock();
    }
  }

  public boolean isPromiscuous()
  {
    try
    {
      _lock.readLock().lock();
      return _isPromiscuous;
    }
    finally
    {
      _lock.readLock().unlock();
    }
  }

  /**
   * adds the credentials to the set of acceptable credentials.
   * 
   * @param credentials
   * @param wantsClockOwnership
   */
  public void grantCredentials(ICredentials credentials,
      boolean wantsClockOwnership)
  {
    try
    {
      _lock.writeLock().lock();

      if (!_validCredentials.contains(credentials))
        _validCredentials.add(credentials);
      else if (LOGGER.isDebugEnabled())
        LOGGER.debug("Credentials are already recognized");

      if (wantsClockOwnership)
      {
        if (_clockOwnerCredentials != null && LOGGER.isWarnEnabled())
          LOGGER
              .warn("Clock owner credentials have already been set, overwriting");

        _clockOwnerCredentials = credentials;
      }
    }
    finally
    {
      _lock.writeLock().unlock();
    }
  }

  /**
   * revoke the credentials. if there is a participant connected with these
   * credentials, they will be immediately disconnected.
   * 
   * @param credentials
   */
  public void revokeCredentials(ICredentials credentials)
  {
    boolean revoked = false;
    try
    {
      _lock.writeLock().lock();

      _pendingCredentials.remove(credentials);
      revoked = _validCredentials.remove(credentials);

      // TODO test for revoked creds same as clock owner
    }
    finally
    {
      _lock.writeLock().unlock();
    }

    if (revoked)
    {
      IIdentifier revokedParticipant = getActiveParticipant(credentials);
      if (revokedParticipant == null) return;

      IoSession session = getParticipantSession(revokedParticipant);
      if (session == null) return;

      if (LOGGER.isDebugEnabled())
        LOGGER.debug("Participant " + revokedParticipant
            + " for revoked credentials is active, closing");

      /*
       * this will trigger the close notification, which will ultimately trigger
       * participantDisconnceted()
       */
      session.close();
    }
  }

  /**
   * return true if these credentials are valid or if we are promiscuous. We do
   * not check that there is no one else using those credentials here..
   * 
   * @param credentials
   * @return
   */
  public boolean credentialsAreValid(ICredentials credentials)
  {
    try
    {
      _lock.readLock().lock();
      return _isPromiscuous || _validCredentials.contains(credentials);
    }
    finally
    {
      _lock.readLock().unlock();
    }
  }

  public boolean isClockOwner(ICredentials credentials)
  {
    try
    {
      _lock.readLock().lock();
      return _clockOwnerCredentials == null
          || _clockOwnerCredentials.equals(credentials);
    }
    finally
    {
      _lock.readLock().unlock();
    }
  }

  /**
   * return all the participants that are actively connected. These participants
   * have all connected and sent all their participant data to reality</br>
   * </br> The container will not be cleared before insertion. If container is
   * null, a new one is created
   */
  public Collection<IIdentifier> getActiveParticipants(
      Collection<IIdentifier> participantContainer)
  {
    if (participantContainer == null)
      participantContainer = new ArrayList<IIdentifier>(Math.max(1,
          _activeParticipantSessions.size()));

    try
    {
      _lock.readLock().lock();
      participantContainer.addAll(_activeParticipantSessions.keySet());
      return participantContainer;
    }
    finally
    {
      _lock.readLock().unlock();
    }
  }

  /**
   * return all the active iosessions
   * 
   * @param container
   * @return
   */
  public Collection<IoSession> getActiveSessions(Collection<IoSession> container)
  {
    if (container == null)
      container = new ArrayList<IoSession>(Math.max(1,
          _activeParticipantSessions.size()));

    try
    {
      _lock.readLock().lock();
      container.addAll(_activeParticipantSessions.values());
      return container;
    }
    finally
    {
      _lock.readLock().unlock();
    }
  }

  /**
   * return the identifier for the participant with the provided credentials, or
   * null
   * 
   * @param credentials
   * @return
   */
  public IIdentifier getActiveParticipant(ICredentials credentials)
  {
    try
    {
      _lock.readLock().lock();
      return _activeParticipantCredentials.get(credentials);
    }
    finally
    {
      _lock.readLock().unlock();
    }
  }

  /**
   * return the {@link IoSession} for the participant. This {@link IIdentifier}
   * must be in the current set returned by {@link #getActiveParticipants()},
   * otherwise it will return null, signaling that the participant has
   * disconnected.
   */
  public IoSession getParticipantSession(IIdentifier participantId)
  {
    try
    {
      _lock.readLock().lock();
      return _activeParticipantSessions.get(participantId);
    }
    finally
    {
      _lock.readLock().unlock();
    }
  }

  public IoSession getPendingParticipantSession(IIdentifier participantId)
  {
    try
    {
      _lock.readLock().lock();
      return _pendingParticipantSessions.get(participantId);
    }
    finally
    {
      _lock.readLock().unlock();
    }
  }

  public IIdentifier getParticipantIdentifier(IoSession session)
  {
    return (IIdentifier) session.getAttribute(IDENTIFIER);
  }

  public ICredentials getParticipantCredentials(IoSession session)
  {
    return (ICredentials) session.getAttribute(CREDENTIALS);
  }

  /**
   * will send message to all participants and wait at most timeToWait
   * milliseconds for acknowledgements from them. If some have not acknowledged
   * by the timeToWait limit, they are returned so that other processing can be
   * performed. </br> If {@link #getParticipantSession(IIdentifier)} returns
   * null for any identifier (disconnected), it will be ignored and added to the
   * list of nonresponders.</br>
   * 
   * @return collection of identifiers of participants who have not responded in
   *         time.
   * @param participants
   * @param message
   * @param timeToWait
   *          or 0 if indefinite
   */
  public Collection<IIdentifier> setState(IParticipant.State state)
  {
    Collection<IIdentifier> participants = getActiveParticipants(new ArrayList<IIdentifier>());
    Map<IIdentifier, Future<IAcknowledgement>> ackMessages = new HashMap<IIdentifier, Future<IAcknowledgement>>();
    Collection<IIdentifier> unresponsive = new ArrayList<IIdentifier>(
        participants.size());

    IControlCommand.State commandState = null;

    switch (state)
    {
      case STARTED:
        if (_reality.getState() == IParticipant.State.SUSPENDED)
          commandState = IControlCommand.State.RESUME;
        else
          commandState = IControlCommand.State.START;
        break;
      case SUSPENDED:
        commandState = IControlCommand.State.SUSPEND;
        break;
      case STOPPED:
        commandState = IControlCommand.State.STOP;
        break;
      case INITIALIZED:
        commandState = IControlCommand.State.RESET;
        break;
      case UNKNOWN:
        commandState = IControlCommand.State.SHUTDOWN;
        break;
    }

    if (LOGGER.isDebugEnabled())
      LOGGER.debug("Signallying participants to " + commandState);

    for (IIdentifier identifier : participants)
    {
      IoSession session = getParticipantSession(identifier);

      if (session == null)
      {
        if (LOGGER.isDebugEnabled())
          LOGGER.debug(identifier
              + " has disconnected, marking as unresponsive");

        unresponsive.add(identifier);

        continue;
      }

      ackMessages.put(identifier, _reality.send(session, new ControlCommand(
          _reality.getIdentifier(), commandState)));
    }

    if (ackMessages.size() != 0)
    {
      Collection<IIdentifier> acknowledged = new HashSet<IIdentifier>();

      long failAfter = System.currentTimeMillis() + getAcknowledgementTimeout();

      long perParticipantWaitTime = Math.min(250, getAcknowledgementTimeout()
          / ackMessages.size());

      while (ackMessages.size() > 0 && System.currentTimeMillis() < failAfter)
      {
        acknowledged.clear();
        if (LOGGER.isDebugEnabled())
          LOGGER.debug("Waiting for acknowledgments from "
              + ackMessages.keySet());

        for (Map.Entry<IIdentifier, Future<IAcknowledgement>> entry : ackMessages
            .entrySet())
          try
          {
            IControlAcknowledgement ack = (IControlAcknowledgement) entry
                .getValue().get(perParticipantWaitTime, TimeUnit.MILLISECONDS);

            if (ack == null || !commandState.equals(ack.getState()))
            {
              /*
               * didn't respond with the correct state..
               */
              unresponsive.add(entry.getKey());

              if (LOGGER.isWarnEnabled())
                LOGGER.warn(entry.getKey() + "'s state reply was "
                    + (ack != null ? ack.getState() : null) + ", expected "
                    + commandState, ack != null ? ack.getException() : null);
            }
            else
              acknowledged.add(entry.getKey());
          }
          catch (TimeoutException e)
          {
            // just fine
            // if (LOGGER.isDebugEnabled())
            // LOGGER.debug("Timed out waiting for " + entry.getKey() + " ["
            // + perParticipantWaitTime + "ms]");
          }
          catch (Exception e)
          {
            /*
             * it's common to not get a response on shutdown, so we just log it
             * and go on
             */
            if (state == State.UNKNOWN)
            {
              if (LOGGER.isDebugEnabled())
                LOGGER.debug("Could not set " + entry.getKey() + " to "
                    + commandState + ". Flagging as unresponsive. ", e);
            }
            else
              LOGGER.error("Could not set " + entry.getKey() + " to "
                  + commandState + ". Flagging as unresponsive. ", e);

            unresponsive.add(entry.getKey());
          }

        ackMessages.keySet().removeAll(unresponsive);
        ackMessages.keySet().removeAll(acknowledged);
      }
    }

    if (unresponsive.size() != 0 && LOGGER.isDebugEnabled())
    {
      LOGGER
          .debug("The following participants did not respond to state change command "
              + commandState + " : " + unresponsive);
      if (_lock.getQueueLength() != 0)
        LOGGER.debug("Threads are still trying to acquire lock : "
            + _lock.getWaitingThreads());
    }

    return unresponsive;
  }

  /**
   * this should, ultimately, check to be sure that the location they report for
   * connecting too is consistent with where they actually are (cheap spoofing
   * measure)
   * 
   * @param session
   * @param addressing
   * @return
   */
  protected boolean isAddressingInfoValid(IoSession session,
      IAddressingInformation addressing)
  {
    return true;
  }

  /**
   * called when a participant connects and asks to be added to the simulation.
   * This will check the participant's credentials and determine whether or not
   * to allow the connection to continue.<br>
   * <br>
   * If the participant is allowed to connect, it wont actually be added to the
   * simulation until after we have received all of its information. At which
   * time, {@link #acceptParticipant()} will be called<br>
   * <br>
   */
  public void participantConnected(final IoSession session,
      IConnectionRequest request)
  {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug(session + " requesting connection");

    ICredentials credentials = request.getCredentials();
    IAddressingInformation addressing = request.getAddressingInformation();
    IIdentifier idTemplate = request.getSource();

    try
    {
      _lock.writeLock().lock();

      /*
       * reject connection immediately if the credentials are invalid
       */
      if (!credentialsAreValid(credentials))
      {
        session.write(
            new ConnectionAcknowledgment(_reality.getIdentifier(), request
                .getMessageId(), "Invalid credentials")).awaitUninterruptibly();
        session.close(false);
        return;
      }

      /*
       * credentials are valid, but are they in use?
       */
      if (_activeParticipantCredentials.containsKey(credentials)
          || _pendingCredentials.contains(credentials))
      {
        session.write(
            new ConnectionAcknowledgment(_reality.getIdentifier(), request
                .getMessageId(), "Credentials already in use"))
            .awaitUninterruptibly();
        session.close(false);
        return;
      }

      /*
       * credentials are unique, we should allow them to connect, but first, is
       * the address info valid?
       */
      if (!isAddressingInfoValid(session, addressing))
      {
        session.write(
            new ConnectionAcknowledgment(_reality.getIdentifier(), request
                .getMessageId(), "Addressing spoof detected"))
            .awaitUninterruptibly();
        session.close(false);
        return;
      }

      /*
       * if the simulation has already stopped, reject the connection
       */
      if (_reality.getState() == IParticipant.State.STOPPED)
      {
        session
            .write(
                new ConnectionAcknowledgment(_reality.getIdentifier(), request
                    .getMessageId(),
                    "Simulation has already stopped, cannot accept new connections"))
            .awaitUninterruptibly();
        session.close(false);
      }

      /*
       * let's assign them their identifier
       */
      final IIdentifier identifier = _reality.newIdentifier(
          _reality.getIdentifier(), idTemplate);

      /*
       * so that if someone connects between now and this participant's
       * acceptance we can correctly reject them
       */
      _pendingCredentials.add(credentials);
      _pendingParticipantSessions.put(identifier, session);

      session.setAttribute(CREDENTIALS, credentials);
      session.setAttribute(IDENTIFIER, identifier);

      if (LOGGER.isDebugEnabled())
        LOGGER.debug("Accepting connection from " + session + " with "
            + credentials + ", assigning " + identifier);

      /*
       * and acknowledge
       */
      session.write(new ConnectionAcknowledgment(_reality.getIdentifier(),
          request.getMessageId(), "Granted", identifier));
    }
    finally
    {
      _lock.writeLock().unlock();
    }
  }

  /**
   * once all the participants information has been received, we officially add
   * them to the simulation and make sure its state is consistent with the
   * simulations
   */
  public boolean acceptParticipant(final IoSession session,
      ISimulationObject object, ObjectCommandHandler objectHandler)
  {
    final IIdentifier identifier = getParticipantIdentifier(session);
    ICredentials credentials = getParticipantCredentials(session);
    boolean isClockOwner = false;
    Future<IAcknowledgement> initAck = null;

    try
    {
      _lock.writeLock().lock();

      _pendingCredentials.remove(credentials);
      _pendingParticipantSessions.remove(identifier);

      if (!identifier.equals(object.getIdentifier()))
        /*
         * these two ids need to match as the object is supposed to represent
         * the participant.. we need to send an appropriate message back..
         */
        throw new IllegalStateException("participant " + identifier
            + " does not match object identifier " + object.getIdentifier());

      /*
       * tell the participant to initialize.. and we'll check that he did
       * later..
       */
      if (LOGGER.isDebugEnabled())
        LOGGER.debug("Requesting " + identifier + " initialize");

      initAck = _reality.send(session,
          new ControlCommand(_reality.getIdentifier(),
              IControlCommand.State.INITIALIZE));

      /*
       * lets notify him about everyone else that is fully connected..
       */
      sendObjectInformation(_reality.getAgentObjectManager(), session,
          identifier);
      sendObjectInformation(_reality.getSensorObjectManager(), session,
          identifier);

      /*
       * it is still possible that another participant will be mid-way through a
       * connection too and will have sent its data. Since data is routed to all
       * active participants, this participant will not have received the data,
       * but will get the add command. In order to defend against this, we send
       * any data that has already been stored that corresponds to the
       * participants in the pending list.. The only reason we know this will
       * actually work is because the object handler is synchronized as well
       * (i.e. no new data can be added to the object handler until we return
       * from this method, as this is called from the object handler..)
       */
      if (_pendingParticipantSessions.size() != 0)
        for (IIdentifier pendingId : _pendingParticipantSessions.keySet())
        {
          Collection<IObjectDelta> data = objectHandler
              .getPendingData(pendingId);
          if (data.size() != 0)
          {
            if (LOGGER.isDebugEnabled())
              LOGGER
                  .debug("Received object data for "
                      + pendingId
                      + ", but no add command. "
                      + identifier
                      + " did not receive data since it was pending too. Forwarding object data");
            session.write(new ObjectData(_reality.getIdentifier(), data));
          }
        }

      /*
       * add them to our list of accepted connections
       */
      _activeParticipantCredentials.put(credentials, identifier);
      _activeParticipantSessions.put(identifier, session);

      /*
       * and lets keep track of the clock owner.. we dont add the owner until we
       * exit the lock. Why? if the clock time is being incremented, the
       * incrementer will own the clock's lock and then try to acquire the read
       * lock at getActiveSessions() to send notification out. However, we
       * currently have the state lock and if we tried to add the clock owner
       * here, we'd try to acquire the clock lock too, resulting in deadlock..
       * so we add after the lock is released
       */
      isClockOwner = isClockOwner(credentials);

      if (LOGGER.isDebugEnabled())
        LOGGER.debug("Particiapnt " + identifier + " has been accepted");
    }
    finally
    {
      _lock.writeLock().unlock();
    }

    if (isClockOwner)
    {
      IClock clock = _reality.getClock();
      OwnedAuthoritativeClock auth = (OwnedAuthoritativeClock) clock
          .getAuthority().get();
      auth.addOwner(identifier);
    }

    final Future<IAcknowledgement> finalInitAck = initAck;

    /**
     * alright, now we post this runnable to the central thread. it's job is to
     * make sure the participant has initialized and to also set its state to be
     * consistent with the system. We do not do this on the current io thread as
     * that would block the processing of acknowledgments. <br>
     * <br>
     * Notice, that we are also acquiring the read lock to make sure that no new
     * connections or state changes occur.
     */
    Runnable setAndWait = new Runnable() {
      public void run()
      {
        boolean disconnect = false;
        try
        {

          _lock.readLock().lock();
          waitForInitializationAck(finalInitAck, identifier);
          startClientIfNecessary(identifier, session);
          suspendClientIfNecessary(identifier, session);

          // /*
          // * has it ack the initialization?
          // */
          // IControlAcknowledgement controlAck = (IControlAcknowledgement)
          // finalInitAck
          // .get(getAcknowledgementTimeout(), TimeUnit.MILLISECONDS);
          //
          // if (controlAck.getState() != IControlCommand.State.INITIALIZE)
          // throw new IllegalStateException("participant " + identifier
          // + " did not initialize : " + controlAck.getState()
          // + ", disconnecting");
          //
          // if (LOGGER.isDebugEnabled())
          // LOGGER.debug(identifier + " has initialized");
          //
          // /*
          // * now we need to check our state and set theirs accordingly...
          // */
          // if (_reality.stateMatches(IParticipant.State.STARTED,
          // IParticipant.State.SUSPENDED))
          // {
          // if (LOGGER.isDebugEnabled())
          // LOGGER.debug("Requesting " + identifier + " start");
          //
          // IControlCommand.State state = ((IControlAcknowledgement) _reality
          // .send(
          // session,
          // new ControlCommand(_reality.getIdentifier(),
          // IControlCommand.State.START)).get(
          // getAcknowledgementTimeout(), TimeUnit.MILLISECONDS))
          // .getState();
          //
          // if (state != IControlCommand.State.START)
          // throw new IllegalStateException("participant " + identifier
          // + " did not start : " + state + ", disconnecting");
          //
          // if (LOGGER.isDebugEnabled())
          // LOGGER.debug(identifier + " has started");
          // }
          //
          // if (_reality.stateMatches(IParticipant.State.SUSPENDED))
          // {
          // if (LOGGER.isDebugEnabled())
          // LOGGER.debug("Requesting " + identifier + " suspend");
          //
          // IControlCommand.State state = ((IControlAcknowledgement) _reality
          // .send(
          // session,
          // new ControlCommand(_reality.getIdentifier(),
          // IControlCommand.State.SUSPEND)).get(
          // getAcknowledgementTimeout(), TimeUnit.MILLISECONDS))
          // .getState();
          //
          // if (state != IControlCommand.State.SUSPEND)
          // throw new IllegalStateException("participant " + identifier
          // + " not suspend : " + state + ", disconnecting");
          //
          // if (LOGGER.isDebugEnabled())
          // LOGGER.debug(identifier + " has suspended");
          // }

        }
        // catch (TimeoutException e)
        // {
        // if (LOGGER.isErrorEnabled())
        // LOGGER.error("Waited too long for control acknowledgement from "
        // + identifier, e);
        // disconnect = true;
        // }
        catch (Exception e)
        {
          if (LOGGER.isErrorEnabled())
            LOGGER.error("Control state acknowledgement from " + identifier
                + " failed ", e);
          disconnect = true;
        }
        finally
        {
          _lock.readLock().unlock();
          if (disconnect) session.close(false);
        }
      }
    };

    _centralExecutor.execute(setAndWait);

    return true;
  }

  /**
   * wait for the client to respond to the ack. Return true if all went well.
   * 
   * @param initializeResponse
   * @return
   */
  protected boolean waitForInitializationAck(
      Future<IAcknowledgement> initializeResponse, IIdentifier client)
  {
    try
    {
      IControlAcknowledgement controlAck = (IControlAcknowledgement) initializeResponse
          .get(getAcknowledgementTimeout(), TimeUnit.MILLISECONDS);

      if (controlAck.getState() != IControlCommand.State.INITIALIZE)
        throw new IllegalStateException("participant " + client
            + " did not initialize : " + controlAck.getState()
            + ", disconnecting");

      return true;
    }
    catch (Exception e)
    {
      LOGGER.error("Failed to wait for initialization of " + client, e);
      return false;
    }
  }

  /**
   * @param client
   * @return true if all is good. false if we should disconnect
   */
  protected boolean startClientIfNecessary(IIdentifier client, IoSession session)
  {
    try
    {
      /*
       * now we need to check our state and set theirs accordingly...
       */
      if (_reality.stateMatches(IParticipant.State.STARTED,
          IParticipant.State.SUSPENDED))
      {
        if (LOGGER.isDebugEnabled())
          LOGGER.debug("Requesting " + client + " start");

        IControlCommand.State state = ((IControlAcknowledgement) _reality.send(
            session,
            new ControlCommand(_reality.getIdentifier(),
                IControlCommand.State.START)).get(getAcknowledgementTimeout(),
            TimeUnit.MILLISECONDS)).getState();

        if (state != IControlCommand.State.START)
          throw new IllegalStateException("participant " + client
              + " did not start : " + state + ", disconnecting");

        if (LOGGER.isDebugEnabled()) LOGGER.debug(client + " has started");
      }

      return true;
    }
    catch (Exception e)
    {
      LOGGER.error("Could not wait for start ", e);
      return false;
    }
  }

  protected boolean suspendClientIfNecessary(IIdentifier client,
      IoSession session)
  {
    try
    {
      if (_reality.stateMatches(IParticipant.State.SUSPENDED))
      {
        if (LOGGER.isDebugEnabled())
          LOGGER.debug("Requesting " + client + " suspend");

        IControlCommand.State state = ((IControlAcknowledgement) _reality.send(
            session,
            new ControlCommand(_reality.getIdentifier(),
                IControlCommand.State.SUSPEND)).get(
            getAcknowledgementTimeout(), TimeUnit.MILLISECONDS)).getState();

        if (state != IControlCommand.State.SUSPEND)
          throw new IllegalStateException("participant " + client
              + " not suspend : " + state + ", disconnecting");

        if (LOGGER.isDebugEnabled()) LOGGER.debug(client + " has suspended");
      }
      return true;
    }
    catch (Exception e)
    {
      LOGGER.error("Could not wait for suspend", e);
      return false;
    }
  }

  /**
   * called when a participant leaves the simulation, cleanly or not
   */
  public IIdentifier participantDisconnected(IoSession session)
  {
    IIdentifier identifier = getParticipantIdentifier(session);
    ICredentials credentials = getParticipantCredentials(session);

    // they never made it past the initial connection
    if (identifier == null || credentials == null) return null;

    if (LOGGER.isDebugEnabled())
      LOGGER.debug("Disconnecting and cleaning up after " + identifier);

    boolean clockWasOwner = false;

    try
    {
      _lock.writeLock().lock();

      session.setAttribute(CREDENTIALS);
      session.setAttribute(IDENTIFIER);

      _pendingCredentials.remove(credentials);

      _activeParticipantCredentials.remove(credentials);
      _activeParticipantSessions.remove(identifier);

      clockWasOwner = isClockOwner(credentials);
    }
    finally
    {
      _lock.writeLock().unlock();
    }

    /*
     * Now we need to zip through all the objects that are owned by this
     * participant (ugh)
     */
    clearObjectInformation(identifier);

    if (clockWasOwner)
    {
      IClock clock = _reality.getClock();
      OwnedAuthoritativeClock auth = (OwnedAuthoritativeClock) clock
          .getAuthority().get();
      auth.removeOwner(identifier);
    }

    if (credentials.equals(_clockOwnerCredentials))
      if (!_reality.stateMatches(IParticipant.State.STOPPED)
          && LOGGER.isWarnEnabled())
        LOGGER
            .warn(identifier
                + " is the clock owner. It's disconnect might adversely affect other participants if they are expected to continue running");

    return identifier;
  }

  /**
   * remove any lingering traces of the participant. This wont do anything if
   * the participant exited cleanly, but if not, we need to deal with what it
   * may have left behind.</br> </br> If it was an agent, there are likely
   * {@link IEfferentCommand}s that need to be dealt with.</br> </br> If it was
   * a sensor, there are {@link IAfferentObject}s, {@link IEfferentObject}
   * s.</br> </br> Both may have introduced {@link IRealObject}s.
   * 
   * @param deadParticipant
   */
  @SuppressWarnings("unchecked")
  private void clearObjectInformation(IIdentifier deadParticipant)
  {
    boolean isAgent = deadParticipant.getType() == IIdentifier.Type.AGENT;

    /*
     * did they exit cleanly?
     */
    if (isAgent
        && _reality.getAgentObjectManager().get(deadParticipant) == null)
      return;

    if (!isAgent
        && _reality.getSensorObjectManager().get(deadParticipant) == null)
      return;

    if (LOGGER.isDebugEnabled())
      LOGGER.debug(deadParticipant
          + " did not leave gracefully, cleaning up loose ends");

    /*
     * nope. handle real objects first
     */

    Collection<IIdentifier> removeMe = new ArrayList<IIdentifier>();

    for (IIdentifier identifier : _reality.getRealObjectManager()
        .getIdentifiers())
      if (deadParticipant.equals(identifier.getOwner()))
      {
        removeMe.add(identifier);
        ((IMutableObjectManager) _reality.getRealObjectManager())
            .remove(identifier);
      }

    /*
     * remove locally
     */

    /*
     * notify everyone
     */
    _reality.send(new ObjectCommand(_reality.getIdentifier(),
        IObjectCommand.Type.REMOVED, removeMe));

    removeMe.clear();

    if (isAgent)
    {
      IIdentifier lastSensor = null;
      for (IIdentifier identifier : _reality.getEfferentCommandManager()
          .getIdentifiersByAgent(deadParticipant))
      {
        ((IMutableObjectManager) _reality.getEfferentCommandManager())
            .remove(identifier);
        IIdentifier sensor = ((ISensoryIdentifier) identifier).getSensor();

        if (lastSensor != null && !lastSensor.equals(sensor))
        {
          // need to flush
          _reality.send(lastSensor, new ObjectCommand(_reality.getIdentifier(),
              IObjectCommand.Type.REMOVED, removeMe));
          removeMe.clear();
        }

        removeMe.add(identifier);
        lastSensor = sensor;
      }

      if (removeMe.size() != 0)
      {
        _reality.send(lastSensor, new ObjectCommand(_reality.getIdentifier(),
            IObjectCommand.Type.REMOVED, removeMe));
        removeMe.clear();
      }
    }
    else
    {
      Collection<IIdentifier> tmpIdentifiers = new ArrayList<IIdentifier>(
          _reality.getAfferentObjectManager().getIdentifiersByAgent(
              deadParticipant));

      ((IMutableObjectManager) _reality.getAfferentObjectManager())
          .remove(tmpIdentifiers);

      tmpIdentifiers.addAll(_reality.getEfferentObjectManager()
          .getIdentifiersByAgent(deadParticipant));

      ((IMutableObjectManager) _reality.getEfferentObjectManager())
          .remove(tmpIdentifiers);

      IIdentifier lastAgent = null;
      for (IIdentifier identifier : tmpIdentifiers)
      {
        IIdentifier agent = ((ISensoryIdentifier) identifier).getSensor();

        if (lastAgent != null && !lastAgent.equals(agent))
        {
          // need to flush
          _reality.send(lastAgent, new ObjectCommand(_reality.getIdentifier(),
              IObjectCommand.Type.REMOVED, removeMe));
          removeMe.clear();
        }

        removeMe.add(identifier);
        lastAgent = agent;
      }

      if (removeMe.size() != 0)
      {
        _reality.send(lastAgent, new ObjectCommand(_reality.getIdentifier(),
            IObjectCommand.Type.REMOVED, removeMe));
        removeMe.clear();
      }
    }

    /*
     * finally, remove the agent/sensor themself
     */
    _reality.send(new ObjectCommand(_reality.getIdentifier(),
        IObjectCommand.Type.REMOVED, Collections.singleton(deadParticipant)));

    if (isAgent)
      ((IMutableObjectManager) _reality.getAgentObjectManager())
          .remove(deadParticipant);
    else
      ((IMutableObjectManager) _reality.getSensorObjectManager())
          .remove(deadParticipant);
  }

  @SuppressWarnings("unchecked")
  private Collection<IIdentifier> sendObjectInformation(
      IObjectManager objectManager, IoSession session, IIdentifier participantId)
  {

    Collection<IIdentifier> identifiers = new HashSet<IIdentifier>();

    identifiers.addAll(objectManager.getIdentifiers());

    Collection<IObjectDelta> data = new ArrayList<IObjectDelta>();

    for (IIdentifier id : identifiers)
      data.add(new FullObjectDelta(objectManager.get(id)));

    if (data.size() != 0)
    {
      session.write(new ObjectData(_reality.getIdentifier(), data));
      session.write(new ObjectCommand(_reality.getIdentifier(),
          IObjectCommand.Type.ADDED, identifiers));
    }

    return identifiers;
  }

  private class TrackedReadWriteLock extends ReentrantReadWriteLock
  {

    public Collection<Thread> getWaitingThreads()
    {
      return getQueuedThreads();
    }
  }
}

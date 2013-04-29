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

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.executor.OrderedThreadPoolExecutor;
import org.commonreality.efferent.IEfferentCommandManager;
import org.commonreality.efferent.impl.EfferentCommandManager;
import org.commonreality.executor.GeneralThreadFactory;
import org.commonreality.identifier.IIdentifier;
import org.commonreality.message.IMessage;
import org.commonreality.message.command.time.TimeCommand;
import org.commonreality.message.credentials.ICredentials;
import org.commonreality.message.request.IAcknowledgement;
import org.commonreality.message.request.IRequest;
import org.commonreality.message.request.object.IObjectDataRequest;
import org.commonreality.mina.protocol.IMINAProtocolConfiguration;
import org.commonreality.mina.service.ClientService;
import org.commonreality.mina.service.IMINAService;
import org.commonreality.mina.service.ServerService;
import org.commonreality.mina.transport.IMINATransportProvider;
import org.commonreality.notification.INotificationManager;
import org.commonreality.notification.impl.NotificationManager;
import org.commonreality.object.manager.IAfferentObjectManager;
import org.commonreality.object.manager.IAgentObjectManager;
import org.commonreality.object.manager.IEfferentObjectManager;
import org.commonreality.object.manager.IMutableObjectManager;
import org.commonreality.object.manager.IObjectManager;
import org.commonreality.object.manager.IRealObjectManager;
import org.commonreality.object.manager.ISensorObjectManager;
import org.commonreality.object.manager.impl.AfferentObjectManager;
import org.commonreality.object.manager.impl.AgentObjectManager;
import org.commonreality.object.manager.impl.EfferentObjectManager;
import org.commonreality.object.manager.impl.RealObjectManager;
import org.commonreality.object.manager.impl.SensorObjectManager;
import org.commonreality.participant.IParticipant;
import org.commonreality.participant.addressing.IAddressingInformation;
import org.commonreality.participant.addressing.impl.BasicAddressingInformation;
import org.commonreality.participant.impl.ack.SessionAcknowledgements;
import org.commonreality.time.IClock;
import org.commonreality.time.ISetableClock;
import org.commonreality.time.impl.net.INetworkedClock;

/**
 * Skeleton participant that handles the majority of tasks. A participants life
 * cycle works like this: Sometime after instantiation, connect will be called
 * which will establish the connection to CommonReality. When common reality is
 * ready to start, it will signal that all sensors and agents should initialize.
 * afetr initializing, start will be signaled. suspend and resume may be called
 * while running The simulation will run for some amount of time, until stop
 * will be called. then either reset or shutdown may be called. shutdown should
 * then disconnect.
 * 
 * @author developer
 */
public abstract class AbstractParticipant implements IParticipant
{
  /**
   * logger definition
   */
  static private final Log                LOGGER = LogFactory
                                                     .getLog(AbstractParticipant.class);

  static private ScheduledExecutorService _periodicExecutor;

  /**
   * return a shared periodic executor that can be useful in many circumstances
   * for periodic events
   * 
   * @return
   */
  static public ScheduledExecutorService getPeriodicExecutor()
  {
    synchronized (AbstractParticipant.class)
    {
      if (_periodicExecutor == null)
        _periodicExecutor = Executors.newScheduledThreadPool(1,
            new GeneralThreadFactory("IParticipant-Periodic"));
      return _periodicExecutor;
    }
  }

  private IIdentifier                      _identifier;

  private IIdentifier                      _commonRealityIdentifier;

  private IoHandler                        _handler;

  private Map<IMINAService, SocketAddress> _services;

  private ExecutorService                  _ioExecutor;

  private volatile State                   _state;

  private Lock                             _stateLock   = new ReentrantLock();

  private Condition                        _stateChange = _stateLock
                                                            .newCondition();

  private IClock                           _clock;

  private ISensorObjectManager             _sensorManager;

  private IAgentObjectManager              _agentManager;

  private IAfferentObjectManager           _afferentManager;

  private IEfferentObjectManager           _efferentManager;

  private IRealObjectManager               _realManager;

  private IEfferentCommandManager          _efferentCommandManager;

  private INotificationManager             _notificationManager;

  private GeneralThreadFactory             _centralThreadFactory;

  private GeneralThreadFactory             _ioThreadFactory;

  public AbstractParticipant(IIdentifier.Type type)
  {
    _services = new HashMap<IMINAService, SocketAddress>();
    _ioExecutor = createIOExecutorService();
    _handler = createIOHandler(type);
    _sensorManager = createSensorObjectManager();
    _agentManager = createAgentObjectManager();
    _afferentManager = createAfferentObjectManager();
    _efferentManager = createEfferentObjectManager();
    _efferentCommandManager = createEfferentCommandManager();
    _realManager = createRealObjectManager();
    _notificationManager = createNotificationManager();
    _state = State.UNKNOWN;
  }

  protected ISensorObjectManager createSensorObjectManager()
  {
    return new SensorObjectManager();
  }

  protected IRealObjectManager createRealObjectManager()
  {
    return new RealObjectManager();
  }

  protected IAgentObjectManager createAgentObjectManager()
  {
    return new AgentObjectManager();
  }

  protected IAfferentObjectManager createAfferentObjectManager()
  {
    return new AfferentObjectManager();
  }

  protected IEfferentObjectManager createEfferentObjectManager()
  {
    return new EfferentObjectManager();
  }

  protected IEfferentCommandManager createEfferentCommandManager()
  {
    return new EfferentCommandManager();
  }

  protected INotificationManager createNotificationManager()
  {
    return new NotificationManager(this);
  }

  public INotificationManager getNotificationManager()
  {
    return _notificationManager;
  }

  public IEfferentCommandManager getEfferentCommandManager()
  {
    return _efferentCommandManager;
  }

  public IRealObjectManager getRealObjectManager()
  {
    return _realManager;
  }

  public void setCommonRealityIdentifier(IIdentifier crId)
  {
    if (_commonRealityIdentifier != null)
      throw new IllegalStateException(
          "CommonReality identifier has already been set");
    _commonRealityIdentifier = crId;
  }

  public IIdentifier getCommonRealityIdentifier()
  {
    return _commonRealityIdentifier;
  }

  abstract public IAddressingInformation getAddressingInformation();

  abstract public ICredentials getCredentials();

  abstract public String getName();

  synchronized protected GeneralThreadFactory getCentralThreadFactory()
  {
    if (_centralThreadFactory == null)
      _centralThreadFactory = new GeneralThreadFactory(getName());
    return _centralThreadFactory;
  }

  synchronized protected GeneralThreadFactory getIOThreadFactory()
  {
    if (_ioThreadFactory == null)
      _ioThreadFactory = new GeneralThreadFactory(getName() + "-IOProcessor",
          getCentralThreadFactory().getThreadGroup());
    return _ioThreadFactory;
  }

  /**
   * creates a single thread pool executor from Mina's orderd thread pool
   * executor so that we can be sure the messages do actually arrive in order.
   * 
   * @return
   */
  protected ExecutorService createIOExecutorService()
  {
    return new OrderedThreadPoolExecutor(1, 1, 5000, TimeUnit.MILLISECONDS,
        getIOThreadFactory());
    // return Executors.newSingleThreadExecutor(getIOThreadFactory());
  }

  final protected Executor getIOExecutor()
  {
    return _ioExecutor;
  }

  /**
   * specify what transport, protocol and address we can accessed on
   * 
   * @param service
   * @param address
   */
  public void addServerService(IMINATransportProvider transport,
      IMINAProtocolConfiguration configuration, SocketAddress address)
  {
    try
    {
      ServerService service = new ServerService();
      service.configure(transport, configuration, getIOHandler(),
          getIOExecutor());
      // if state is anything but unknown, we need to start the service
      if (!stateMatches(State.UNKNOWN)) startService(service, address);
      _services.put(service, address);
    }
    catch (Exception e)
    {
      LOGGER.error("Could not start server service ", e);
    }
  }

  /**
   * specify what transport, protocol and address we can use to connect to
   * another participant (usually, just common reality)
   * 
   * @param service
   * @param address
   */
  public void addClientService(IMINATransportProvider transport,
      IMINAProtocolConfiguration configuration, SocketAddress address)
  {
    try
    {
      ClientService service = new ClientService();
      service.configure(transport, configuration, getIOHandler(),
          getIOExecutor());
      // if state is anything but unknown, we need to start the service
      if (!stateMatches(State.UNKNOWN)) startService(service, address);
      _services.put(service, address);
    }
    catch (Exception e)
    {
      LOGGER.error("Could not start server service ", e);
    }
  }

  private void startService(IMINAService service, SocketAddress address)
      throws Exception
  {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug(getName() + " Starting "
          + service.getClass().getSimpleName() + " on " + address);
    service.start(address);
  }

  private void stopService(IMINAService service, SocketAddress address)
      throws Exception
  {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("Stopping " + service.getClass().getSimpleName() + " on "
          + address);
    service.stop(address);
  }

  private void setState(State state)
  {
    try
    {
      _stateLock.lock();
      if (LOGGER.isDebugEnabled())
        LOGGER.debug(getName() + " Setting state to " + state + " from "
            + _state);
      _state = state;
      _stateChange.signalAll();
    }
    finally
    {
      _stateLock.unlock();
    }
  }

  final public State waitForState(State... states) throws InterruptedException
  {
    return waitForState(0, states);
  }

  private boolean matches(State... states)
  {
    for (State test : states)
      if (test == _state) return true;
    return false;
  }

  final public State waitForState(long waitTime, State... states)
      throws InterruptedException
  {
    try
    {
      _stateLock.lock();

      if (waitTime <= 0)
        while (!matches(states))
          _stateChange.await();
      else
      {
        long endTime = System.currentTimeMillis() + waitTime;
        while (!matches(states) && System.currentTimeMillis() < endTime)
          _stateChange.await(waitTime, TimeUnit.MILLISECONDS);
      }

      return _state;
    }
    finally
    {
      _stateLock.unlock();
    }
  }

  final public State getState()
  {
    try
    {
      _stateLock.lock();
      return _state;
    }
    finally
    {
      _stateLock.unlock();
    }
  }

  public boolean stateMatches(State... states)
  {
    State state = getState();
    for (State test : states)
      if (state == test) return true;
    return false;
  }

  /**
   * checks the state to see if it matches one of these, if not, it fires an
   * exception
   * 
   * @param states
   */
  protected void checkState(State... states)
  {
    State state = getState();
    StringBuilder sb = new StringBuilder("(");
    for (State test : states)
      if (test == state)
        return;
      else
        sb.append(test).append(", ");

    if (sb.length() > 1) sb.delete(sb.length() - 2, sb.length());
    sb.append(")");

    throw new IllegalStateException("Current state (" + state
        + ") is invalid, expecting " + sb);
  }

  /**
   * called after the connection has been established..
   * 
   * @param identifier
   */
  public void setIdentifier(IIdentifier identifier)
  {
    if (getIdentifier() != null)
      throw new RuntimeException("identifier is already set");
    _identifier = identifier;

    setState(State.CONNECTED);
  }

  /**
   * we don't have a valid identifier until we have connected to reality
   * 
   * @see org.commonreality.identifier.IIdentifiable#getIdentifier()
   */
  public IIdentifier getIdentifier()
  {
    return _identifier;
  }

  public void configure(Map<String, String> options) throws Exception
  {
    checkState(State.CONNECTED, State.INITIALIZED, State.STOPPED, State.UNKNOWN);
  }

  /**
   * called in response to a command from Reality to get everything ready to
   * run. we must be connected first.
   */
  public void initialize() throws Exception
  {
    checkState(State.CONNECTED);

    setState(State.INITIALIZED);
  }

  /**
   * called to actually start this participant
   */
  public void start() throws Exception
  {
    checkState(State.INITIALIZED);

    if (LOGGER.isDebugEnabled()) LOGGER.debug("Started " + getName());
    setState(State.STARTED);
  }

  /**
   * called when this participant needs to stop
   */
  public void stop() throws Exception
  {
    checkState(State.STARTED, State.SUSPENDED);
    if (LOGGER.isDebugEnabled()) LOGGER.debug("Stopped " + getName());
    setState(State.STOPPED);
  }

  public void suspend() throws Exception
  {
    checkState(State.STARTED);
    setState(State.SUSPENDED);
  }

  public void resume() throws Exception
  {
    checkState(State.SUSPENDED);
    setState(State.STARTED);
  }

  /**
   * called when we are to reset to a post-initialize state. this impl attempts
   * to reset the clock if it is INetworked or ISettabl
   */
  public void reset(boolean clockWillBeReset) throws Exception
  {
    checkState(State.STOPPED, State.INITIALIZED);

    if (clockWillBeReset)
    {
      IClock clock = getClock();
      if (clock instanceof INetworkedClock)
      {
        if (LOGGER.isDebugEnabled()) LOGGER.debug("Reseting network clock");
        ((INetworkedClock) clock)
            .setCurrentTimeCommand(new TimeCommand(null, 0));
      }
      else if (clock instanceof ISetableClock)
      {
        if (LOGGER.isDebugEnabled()) LOGGER.debug("Reseting setable clock");
        ((ISetableClock) clock).setTime(0);
      }
    }

    setState(State.INITIALIZED);
  }

  public void shutdown(boolean force) throws Exception
  {
    if (!force)
      checkState(State.STOPPED, State.CONNECTED, State.INITIALIZED,
          State.UNKNOWN);

    try
    {
      disconnect(force);
    }
    catch (Exception e)
    {
      LOGGER.error("Exception ", e);
    }

    clearObjectManagers();

    /*
     * and kill the executor
     */
    ExecutorService ex = (ExecutorService) getIOExecutor();
    if (!ex.isShutdown()) ex.shutdown();

    /**
     * release the thread groups
     */
    getIOThreadFactory().dispose();
    getCentralThreadFactory().dispose();
  }

  /**
   * 
   */
  public void shutdown() throws Exception
  {
    shutdown(false);
  }

  /**
   * 
   */
  @SuppressWarnings("unchecked")
  protected void clearObjectManagers()
  {
    IObjectManager om = getAfferentObjectManager();
    if (om instanceof IMutableObjectManager)
      ((IMutableObjectManager) om).remove(om.getIdentifiers());

    om = getEfferentCommandManager();
    if (om instanceof IMutableObjectManager)
      ((IMutableObjectManager) om).remove(om.getIdentifiers());

    om = getEfferentObjectManager();
    if (om instanceof IMutableObjectManager)
      ((IMutableObjectManager) om).remove(om.getIdentifiers());

    om = getAgentObjectManager();
    if (om instanceof IMutableObjectManager)
      ((IMutableObjectManager) om).remove(om.getIdentifiers());

    om = getSensorObjectManager();
    if (om instanceof IMutableObjectManager)
      ((IMutableObjectManager) om).remove(om.getIdentifiers());
  }

  /**
   * @bug this starts all services in a random order. it should be server first
   *      then clients to avoid deadlock in complex configurations
   * @see org.commonreality.participant.IParticipant#connect()
   */
  public void connect() throws Exception
  {
    checkState(State.UNKNOWN);

    Exception differed = null;
    for (Map.Entry<IMINAService, SocketAddress> entry : _services.entrySet())
      try
      {
        startService(entry.getKey(), entry.getValue());
      }
      catch (Exception e)
      {
        LOGGER.error("Could not start service ", e);
        differed = e;
      }

    if (differed != null) throw differed;

    // moved to setIdentifier()
    // setState(State.CONNECTED);
  }

  public void disconnect() throws Exception
  {
    disconnect(false);
  }

  public void disconnect(boolean force) throws Exception
  {
    if (!force)
      checkState(State.CONNECTED, State.INITIALIZED, State.STOPPED,
          State.UNKNOWN);

    Exception differed = null;
    for (Map.Entry<IMINAService, SocketAddress> entry : _services.entrySet())
      try
      {
        stopService(entry.getKey(), entry.getValue());
      }
      catch (Exception e)
      {
        LOGGER.error("Could not stop service ", e);
        differed = e;
      }
    _services.clear();

    setState(State.UNKNOWN);

    if (differed != null) throw differed;
  }

  protected Collection<IAddressingInformation> getServerAddressInformation()
  {
    ArrayList<IAddressingInformation> rtn = new ArrayList<IAddressingInformation>();
    for (Map.Entry<IMINAService, SocketAddress> entry : _services.entrySet())
      if (entry.getKey() instanceof ServerService)
        rtn.add(new BasicAddressingInformation(entry.getValue()));
    return rtn;
  }

  /**
   * return the clock that this participant has access to
   * 
   * @return
   */
  public IClock getClock()
  {
    return _clock;
  }

  protected void setClock(IClock clock)
  {
    _clock = clock;
  }

  /**
   * send a message to common reality. We also cache any data requests going out
   * and store them temporarily. they will not be applied to the respective
   * object manager until we get confirmation from CR
   * 
   * @param message
   */
  public Future<IAcknowledgement> send(IMessage message)
  {
    BasicParticipantIOHandler handler = (BasicParticipantIOHandler) getIOHandler();

    /*
     * anytime we send data out, we store it because we wont actually set the
     * data until we get confirmation back from CR in the form of the
     * IObjectCommand. However, if the data request is going out to everyone (in
     * the case of a RealObject, AgentObject or SensorObject) we will get the
     * data sent to us, so we dont bother storing it (otherwise, we'd get double
     * data)
     */
    if (message instanceof IObjectDataRequest
        && !IIdentifier.ALL.equals(((IObjectDataRequest) message)
            .getDestination()))
      handler.getObjectHandler().storeObjectData(
          ((IObjectDataRequest) message).getData(), message);

    IoSession session = handler.getCommonRealitySession();

    Future<IAcknowledgement> rtn = SessionAcknowledgements.EMPTY;

    if (session != null)
      synchronized (session)
      {
        if (message instanceof IRequest)
        {
          SessionAcknowledgements sa = SessionAcknowledgements
              .getSessionAcks(session);
          if (sa != null) rtn = sa.newAckFuture(message);
        }

        session.write(message);
      }
    else if (LOGGER.isDebugEnabled())
      LOGGER.debug("Null session, could not send");

    return rtn;
  }

  protected IoHandler createIOHandler(IIdentifier.Type type)
  {
    return new BasicParticipantIOHandler(this, type);
  }

  final protected IoHandler getIOHandler()
  {
    return _handler;
  }

  public ISensorObjectManager getSensorObjectManager()
  {
    return _sensorManager;
  }

  public IAfferentObjectManager getAfferentObjectManager()
  {
    return _afferentManager;
  }

  public IEfferentObjectManager getEfferentObjectManager()
  {
    return _efferentManager;
  }

  public IAgentObjectManager getAgentObjectManager()
  {
    return _agentManager;
  }

}

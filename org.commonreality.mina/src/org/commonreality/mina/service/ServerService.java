/*
 * Created on Feb 22, 2007 Copyright (C) 2001-6, Anthony Harrison anh23@pitt.edu
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
package org.commonreality.mina.service;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.IoServiceListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteToClosedSessionException;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.filter.executor.OrderedThreadPoolExecutor;
import org.apache.mina.handler.demux.DemuxingIoHandler;
import org.apache.mina.handler.demux.ExceptionHandler;
import org.apache.mina.handler.demux.MessageHandler;
import org.commonreality.mina.MINASessionInfo;
import org.commonreality.net.handler.IMessageHandler;
import org.commonreality.net.protocol.IProtocolConfiguration;
import org.commonreality.net.service.IServerService;
import org.commonreality.net.session.ISessionListener;
import org.commonreality.net.transport.ITransportProvider;

/**
 * @author developer
 */
public class ServerService implements IServerService
{

  /**
   * logger definition
   */
  static private final Log   LOGGER = LogFactory.getLog(ServerService.class);

  private ITransportProvider _provider;

  private DemuxingIoHandler  _handler;

  private IoAcceptor         _acceptor;

  private ExecutorService    _actualExecutor;

  @SuppressWarnings("unchecked")
  @Override
  public void configure(ITransportProvider transport,
      IProtocolConfiguration protocol,
      Map<Class<?>, IMessageHandler<?>> defaultHandlers,
      final ISessionListener defaultListener, ThreadFactory factory)
  {
    _provider = transport;
    _acceptor = (IoAcceptor) _provider.configureServer();

    _handler = new DemuxingIoHandler() {

      @Override
      public void sessionCreated(IoSession session) throws Exception
      {
        if (defaultListener != null)
          defaultListener.created(MINASessionInfo.asSessionInfo(session));
      }

      @Override
      public void sessionOpened(IoSession session) throws Exception
      {
        if (defaultListener != null)
          defaultListener.opened(MINASessionInfo.asSessionInfo(session));
      }

      @Override
      public void sessionClosed(IoSession session) throws Exception
      {
        if (defaultListener != null)
        {
          defaultListener.closed(MINASessionInfo.asSessionInfo(session));
          defaultListener.destroyed(MINASessionInfo.asSessionInfo(session));
        }
      }

    };

    _handler.addSentMessageHandler(Object.class, new MessageHandler<Object>() {

      @Override
      public void handleMessage(IoSession arg0, Object arg1) throws Exception
      {
        // silent noop
        if (LOGGER.isDebugEnabled())
          LOGGER.debug(String.format("Sent %s", arg1));
      }

    });

    _handler.addReceivedMessageHandler(Object.class,
        new MessageHandler<Object>() {

          @Override
          public void handleMessage(IoSession arg0, Object arg1)
              throws Exception
          {
            if (LOGGER.isErrorEnabled())
              LOGGER.error(String.format("Received unexpected message(%s): %s",
                  arg1.getClass().getName(), arg1));
          }

        });

    _handler.addExceptionHandler(Throwable.class,
        new ExceptionHandler<Throwable>() {

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
            LOGGER.error("Exception caught from session " + session
                + ", closing. ", exception);

            if (!session.isClosing()) session.close(false);
          }

        });

    defaultHandlers.entrySet().forEach((e) -> {
      final Class<?> clazz = e.getKey();
      IMessageHandler<Object> handler = (IMessageHandler<Object>) e.getValue();
      _handler.addReceivedMessageHandler(clazz, new MessageHandler<Object>() {
        public void handleMessage(IoSession session, Object msg)
            throws Exception
        {
          handler.accept(MINASessionInfo.asSessionInfo(session), msg);
        }
      });
    });

    _acceptor.setHandler(_handler);

    protocol.configure(_acceptor); // mina versions will expect an IoService

    _actualExecutor = createIOExecutor(factory);
    if (_actualExecutor != null)
      _acceptor.getFilterChain().addLast("executor",
          new ExecutorFilter(_actualExecutor));
  }

  protected ExecutorService createIOExecutor(ThreadFactory factory)
  {
    /*
     * 
     */
    int max = Integer.parseInt(System.getProperty(
        "defaultReality.ioMaxThreads", Integer.toString(Integer.MAX_VALUE)));
    return new OrderedThreadPoolExecutor(1, max, 10000, TimeUnit.MILLISECONDS,
        factory);
  }

  /**
   * @see org.commonreality.mina.service.IMINAService#start()
   */
  public SocketAddress start(SocketAddress address) throws Exception
  {
    AcceptorListener listener = new AcceptorListener();
    _acceptor.addListener(listener);
    _acceptor.bind(address);
    SocketAddress addr = listener.get();
    _acceptor.removeListener(listener);
    return addr;
  }

  /**
   * @see org.commonreality.mina.service.IMINAService#stop()
   */
  public void stop(SocketAddress address) throws Exception
  {
    if (_acceptor == null)
    {
      if (LOGGER.isWarnEnabled()) LOGGER.warn("Already stopped");
      return;
    }

    AcceptorListener listener = new AcceptorListener();
    _acceptor.addListener(listener);
    _acceptor.unbind(address);
    listener.get();
    _acceptor.removeListener(listener);

    _acceptor.dispose();

    if (_actualExecutor != null)
    {
      _actualExecutor.shutdown();
      _actualExecutor = null;
    }

    _acceptor = null;
    _handler = null;
    _provider = null;
  }

  /**
   * listener Future hybrid that waits until the acceptor has started, then
   * snags the actual SocketAddress that it is connected on.
   * 
   * @author developer
   */
  static private class AcceptorListener extends FutureTask<SocketAddress>
      implements IoServiceListener
  {
    protected SocketAddress _address = null;

    public AcceptorListener()
    {
      super(new Callable<SocketAddress>() {
        public SocketAddress call()
        {
          return null;
        }
      });
    }

    /**
     * @see org.apache.mina.common.IoServiceListener#serviceActivated(org.apache.mina.common.IoService,
     *      java.net.SocketAddress, org.apache.mina.common.IoHandler,
     *      org.apache.mina.common.IoServiceConfig)
     */
    public void serviceActivated(IoService service)
    {
      // called first.. so that the null callable in the constructor has no
      // effect
      set(((IoAcceptor) service).getLocalAddress());
      run();
    }

    /**
     * @see org.apache.mina.common.IoServiceListener#serviceDeactivated(org.apache.mina.common.IoService,
     *      java.net.SocketAddress, org.apache.mina.common.IoHandler,
     *      org.apache.mina.common.IoServiceConfig)
     */
    public void serviceDeactivated(IoService service)
    {
      set(((IoAcceptor) service).getLocalAddress());
      run();
    }

    /**
     * @see org.apache.mina.common.IoServiceListener#sessionCreated(org.apache.mina.common.IoSession)
     */
    public void sessionCreated(IoSession arg0)
    {

    }

    /**
     * @see org.apache.mina.common.IoServiceListener#sessionDestroyed(org.apache.mina.common.IoSession)
     */
    public void sessionDestroyed(IoSession arg0)
    {

    }

    public void serviceIdle(IoService arg0, IdleStatus arg1)
    {

    }

  }

}

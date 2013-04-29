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
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.IoServiceListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.commonreality.mina.protocol.IMINAProtocolConfiguration;
import org.commonreality.mina.transport.IMINATransportProvider;

/**
 * @author developer
 */
public class ServerService implements IMINAService
{
  /**
   * logger definition
   */
  static private final Log       LOGGER = LogFactory
                                            .getLog(ServerService.class);

  private IMINATransportProvider _provider;

  private IoHandler              _handler;

  private IoAcceptor             _acceptor;

  public void configure(IMINATransportProvider provider,
      IMINAProtocolConfiguration protocol, IoHandler handler,
      Executor serviceExecutor)
  {
    _provider = provider;
    _handler = handler;

    _acceptor = _provider.createAcceptor();
    _acceptor.setHandler(_handler);

    protocol.configure(_acceptor);

    if (serviceExecutor != null)
      _acceptor.getFilterChain().addLast("threadPool",
          new ExecutorFilter(serviceExecutor));
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

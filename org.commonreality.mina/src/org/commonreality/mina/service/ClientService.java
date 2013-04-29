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
import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.commonreality.mina.protocol.IMINAProtocolConfiguration;
import org.commonreality.mina.transport.IMINATransportProvider;

/**
 * @author developer
 */
public class ClientService implements IMINAService
{
  /**
   * logger definition
   */
  static private final Log       LOGGER = LogFactory
                                            .getLog(ClientService.class);

  private IMINATransportProvider _provider;

  private IoHandler              _handler;

  private IoConnector            _connector;

  /**
   * @see org.commonreality.mina.service.IMINAService#configure(org.commonreality.mina.transport.IMINATransportProvider,
   *      java.net.SocketAddress, org.apache.mina.common.IoHandler)
   */
  public void configure(IMINATransportProvider provider,
      IMINAProtocolConfiguration protocol, IoHandler handler,
      Executor serviceExecutor)
  {
    _provider = provider;
    _handler = handler;

    _connector = _provider.createConnector();
    _connector.setHandler(_handler);

    protocol.configure(_connector);

    if (serviceExecutor != null)
      _connector.getFilterChain().addLast("threadPool",
          new ExecutorFilter(serviceExecutor));
  }

  /**
   * @see org.commonreality.mina.service.IMINAService#start()
   */
  public SocketAddress start(SocketAddress address) throws Exception
  {
    if (LOGGER.isDebugEnabled()) LOGGER.debug("Connecting to " + address);
    ConnectFuture future = _connector.connect(address);
    future.awaitUninterruptibly();
    return future.getSession().getLocalAddress();
  }

  /**
   * @see org.commonreality.mina.service.IMINAService#stop()
   */
  public void stop(SocketAddress address) throws Exception
  {
    if (_connector == null)
    {
      if (LOGGER.isWarnEnabled()) LOGGER.warn("Already stopped");
      return;
    }

    for (IoSession session : _connector.getManagedSessions().values())
      if (session.getRemoteAddress().equals(address))
        try
        {
          session.close(true).awaitUninterruptibly();
        }
        catch (Exception e)
        {
          if (LOGGER.isDebugEnabled())
            LOGGER.debug("Exception while closing connection", e);
        }

    if (_connector != null) _connector.dispose();

    _connector = null;
    _handler = null;
    _provider = null;
  }

}

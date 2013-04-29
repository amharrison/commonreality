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

import org.apache.mina.core.service.IoHandler;
import org.commonreality.mina.protocol.IMINAProtocolConfiguration;
import org.commonreality.mina.transport.IMINATransportProvider;

/**
 * Takes a transport provider and starts a specific service
 * 
 * @author developer
 */
public interface IMINAService
{

  /**
   * configure the service
   * 
   * @param provider
   *            transport to use
   * @param protocol
   *            protocol
   * @param handler
   * @param serviceExecutor
   *            the executor on which messages will be delivered on or null if
   *            the IO processor should finish the job
   */
  public void configure(IMINATransportProvider provider,
      IMINAProtocolConfiguration protocol, IoHandler handler,
      Executor serviceExecutor);



  /**
   * start this service on a requested address. returns the actual address that
   * we are connected on
   * 
   * @param address
   * @return
   * @throws Exception
   */
  public SocketAddress start(SocketAddress address) throws Exception;

  /**
   * stop this service on this address
   * 
   * @param address,
   *            the result from start()
   * @throws Exception
   */
  public void stop(SocketAddress address) throws Exception;
  

  // public void waitForStart() throws Exception;
  // public void waitForStop() throws Exception;
}

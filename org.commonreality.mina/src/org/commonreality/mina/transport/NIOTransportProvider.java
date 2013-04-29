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
package org.commonreality.mina.transport;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.transport.socket.SocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

/**
 * when using an NIOTransportProvider you must ensure that the executor has more
 * than one thread
 * 
 * @author developer
 */
public class NIOTransportProvider implements IMINATransportProvider
{
  /**
   * logger definition
   */
  static private final Log LOGGER = LogFactory
                                      .getLog(NIOTransportProvider.class);

  /**
   * @see org.commonreality.mina.transport.IMINATransportProvider#createAcceptor()
   */
  public IoAcceptor createAcceptor()
  {
    SocketAcceptor sa = new NioSocketAcceptor();
    return sa;
  }

  /**
   * @see org.commonreality.mina.transport.IMINATransportProvider#createConnector()
   */
  public IoConnector createConnector()
  {
    return new NioSocketConnector();
  }

  /**
   * @see org.commonreality.mina.transport.IMINATransportProvider#createConfiguration()
   */
//  public IoServiceConfig createAcceptorConfiguration()
//  {
//    IoServiceConfig conf = new SocketAcceptorConfig();
//    conf.setThreadModel(ThreadModel.MANUAL);
//
//    return conf;
//  }
//
//  public IoServiceConfig createConnectorConfiguration()
//  {
//    IoServiceConfig conf = new SocketConnectorConfig();
//    conf.setThreadModel(ThreadModel.MANUAL);
//    return conf;
//  }

  /**
   * possible options: int,String port String hostname, int port InetAddress ip,
   * int port port =0 is not permitted
   * 
   * @see org.commonreality.mina.transport.IMINATransportProvider#createAddress(java.lang.Object[])
   */
  public SocketAddress createAddress(Object... args)
  {
    if (args.length > 2 || args.length == 0)
      throw new IllegalArgumentException(
          "Must have 1 or 2 parameters to specify an InetSocketAddress");

    int port = -1;
    Object last = args[args.length - 1];
    Object first = args[0];

    if (last instanceof String)
      port = Integer.parseInt((String) last);
    else if (last instanceof Number) port = ((Number) last).intValue();

    if (port < 0)
      throw new IllegalArgumentException(
          "port must be greater than or equal to zero. got " + port + " form " +
              last);

    if (last == first) return new InetSocketAddress(port);

    /*
     * now we check the first item
     */
    if (first instanceof String)
      return new InetSocketAddress((String) first, port);

    if (first instanceof InetAddress)
      return new InetSocketAddress((InetAddress) first, port);

    throw new IllegalArgumentException("Could not get host information from " +
        first);
  }
}

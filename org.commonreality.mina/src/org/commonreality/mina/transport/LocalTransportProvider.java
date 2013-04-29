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

import java.net.SocketAddress;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.transport.vmpipe.VmPipeAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.mina.transport.vmpipe.VmPipeConnector;

/**
 * @author developer
 */
public class LocalTransportProvider implements IMINATransportProvider
{
  /**
   * logger definition
   */
  static private final Log             LOGGER        = LogFactory
                                                         .getLog(LocalTransportProvider.class);


  /**
   * @see org.commonreality.mina.transport.IMINATransportProvider#createAcceptor()
   */
  public IoAcceptor createAcceptor()
  {
    return new VmPipeAcceptor();
  }

  /**
   * @see org.commonreality.mina.transport.IMINATransportProvider#createConnector()
   */
  public IoConnector createConnector()
  {
    return new VmPipeConnector();
  }

  /**
   * @see org.commonreality.mina.transport.IMINATransportProvider#createConfiguration()
   */
//  public IoServiceConfig createAcceptorConfiguration()
//  {
//    IoServiceConfig conf = new BaseIoAcceptorConfig() {
//      public IoSessionConfig getSessionConfig()
//      {
//        return CONFIG;
//      }
//    };
//    conf.setThreadModel(ThreadModel.MANUAL);
//    return conf;
//  }
//  
//  public IoServiceConfig createConnectorConfiguration()
//  {
//    IoServiceConfig conf = new BaseIoConnectorConfig() {
//      public IoSessionConfig getSessionConfig()
//      {
//        return CONFIG;
//      }
//    };
//    conf.setThreadModel(ThreadModel.MANUAL);
//    
//    return conf;
//  }

  /** 
   * @see org.commonreality.mina.transport.IMINATransportProvider#createAddress(java.lang.Object[])
   */
  public SocketAddress createAddress(Object... args)
  {
    if(args.length!=1)
      throw new IllegalArgumentException("Must only provide one integer or long");
    
    Object arg = args[0];
    int port = -1;
    
    if(arg instanceof String)
      port = Integer.parseInt((String)arg);
    else
      if(arg instanceof Number)
        port = ((Number)arg).intValue();
    
    if(port>0)
      return new VmPipeAddress(port);
    
    throw new IllegalArgumentException("Invalid port specified, got "+port+" from "+arg);
  }
}

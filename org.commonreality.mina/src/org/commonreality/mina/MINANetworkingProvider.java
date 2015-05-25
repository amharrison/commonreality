package org.commonreality.mina;

/*
 * default logging
 */
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.mina.protocol.NOOPProtocol;
import org.commonreality.mina.protocol.SerializingProtocol;
import org.commonreality.mina.service.ClientService;
import org.commonreality.mina.service.ServerService;
import org.commonreality.mina.transport.LocalTransportProvider;
import org.commonreality.mina.transport.NIOTransportProvider;
import org.commonreality.net.protocol.IProtocolConfiguration;
import org.commonreality.net.provider.INetworkingProvider;
import org.commonreality.net.service.IClientService;
import org.commonreality.net.service.IServerService;
import org.commonreality.net.transport.ITransportProvider;

public class MINANetworkingProvider implements INetworkingProvider
{
  /**
   * Logger definition
   */
  static private final transient Log                LOGGER               = LogFactory
                                                                             .getLog(MINANetworkingProvider.class);

  final private Map<String, ITransportProvider>     _availableTransports = new TreeMap<String, ITransportProvider>();

  final private Map<String, IProtocolConfiguration> _availableProtocols  = new TreeMap<String, IProtocolConfiguration>();

  public MINANetworkingProvider()
  {
    _availableTransports.put(NIO_TRANSPORT, new NIOTransportProvider());
    _availableTransports.put(NOOP_TRANSPORT, new LocalTransportProvider());

    _availableProtocols.put(NOOP_PROTOCOL, new NOOPProtocol());
    _availableProtocols.put(SERIALIZED_PROTOCOL, new SerializingProtocol());
  }

  @Override
  public IServerService newServer()
  {
    return new ServerService();
  }

  @Override
  public IClientService newClient()
  {
    return new ClientService();
  }

  @Override
  public IProtocolConfiguration getProtocol(String protocolType)
  {
    return _availableProtocols.get(protocolType);
  }

  @Override
  public ITransportProvider getTransport(String transportType)
  {
    return _availableTransports.get(transportType);
  }

}

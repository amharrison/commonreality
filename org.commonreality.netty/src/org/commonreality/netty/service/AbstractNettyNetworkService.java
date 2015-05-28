package org.commonreality.netty.service;

/*
 * default logging
 */
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.net.handler.IMessageHandler;
import org.commonreality.net.handler.MessageMultiplexer;
import org.commonreality.net.service.INetworkService;
import org.commonreality.net.session.ISessionInfo;

public abstract class AbstractNettyNetworkService implements INetworkService
{
  static private final Log     LOGGER = LogFactory
                                          .getLog(AbstractNettyNetworkService.class);

  protected EventLoopGroup     _workerGroup;

  protected MessageMultiplexer _multiplexer;

  protected EventLoopGroup createWorkerGroup(int numClients,
      ThreadFactory threadFactory)
  {
    EventLoopGroup elg = new NioEventLoopGroup(numClients,
        Executors.newCachedThreadPool(threadFactory));
    return elg;
  }

  protected EventLoopGroup createServerGroup(ThreadFactory threadFactory)
  {
    EventLoopGroup elg = new NioEventLoopGroup(1,
        Executors.newSingleThreadExecutor(threadFactory));
    return elg;
  }

  protected MessageMultiplexer createMultiplexer(
      Map<Class<?>, IMessageHandler<?>> defaultHandlers)
  {
    IMessageHandler<Object> def = new IMessageHandler<Object>() {

      @Override
      public void accept(ISessionInfo<?> t, Object u)
      {
        if (LOGGER.isWarnEnabled())
          LOGGER.warn(String.format("Unprocessed message %s", u));
      }
      
    };
    
    MessageMultiplexer mm = new MessageMultiplexer(def);

    for (Map.Entry<Class<?>, IMessageHandler<?>> e : defaultHandlers.entrySet())
    {
      if (LOGGER.isDebugEnabled())
        LOGGER.debug(String.format("Adding handler for %s", e.getKey()
            .getName()));
      mm.add(e.getKey(), e.getValue());
    }

    return mm;
  }
}
package org.commonreality.net.handler;

/*
 * default logging
 */
import java.util.HashMap;
import java.util.Map;

import org.commonreality.net.session.ISessionInfo;

/**
 * Multiplexer that uses strict class types to route messages.
 * 
 * @author harrison
 */
public class MessageMultiplexer implements IMessageHandler<Object>
{

  final private Map<Class, IMessageHandler<?>> _handlers = new HashMap<Class, IMessageHandler<?>>();

  final private IMessageHandler<Object>        _fallThrough;

  /**
   * @param fallThrough
   *          called for any unprocessed message
   */
  public MessageMultiplexer(IMessageHandler<Object> fallThrough)
  {
    _fallThrough = fallThrough;
  }

  public <M> void add(Class<M> clazz, IMessageHandler<M> handler)
  {
    _handlers.put(clazz, handler);
  }

  @Override
  public void accept(ISessionInfo t, Object u)
  {
    IMessageHandler<Object> handler = (IMessageHandler<Object>) _handlers.get(u
        .getClass());
    if (handler != null)
      handler.accept(t, u);
    else
      _fallThrough.accept(t, u);
  }

}

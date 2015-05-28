package org.commonreality.mina;

/*
 * default logging
 */
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.IoServiceListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.handler.demux.DemuxingIoHandler;
import org.apache.mina.handler.demux.ExceptionHandler;
import org.apache.mina.handler.demux.MessageHandler;
import org.commonreality.net.filter.IMessageFilter;
import org.commonreality.net.handler.IExceptionHandler;
import org.commonreality.net.handler.IMessageHandler;
import org.commonreality.net.impl.AbstractSessionInfo;
import org.commonreality.net.session.ISessionListener;
import org.commonreality.net.transform.IMessageTransfromer;

public class MINASessionInfo extends AbstractSessionInfo<IoSession>
{

  static public MINASessionInfo asSessionInfo(IoSession session)
  {
    MINASessionInfo si = (MINASessionInfo) session
        .getAttribute("MINAsessionWrapper");
    if (si == null)
    {
      si = new MINASessionInfo(session);
      session.setAttribute("MINAsessionWrapper", si);
    }

    return si;
  }

  /**
   * Logger definition
   */
  static private final transient Log    LOGGER         = LogFactory
                                                           .getLog(MINASessionInfo.class);

  private Lock                          _lock          = new ReentrantLock();

  private Condition                     _connected     = _lock.newCondition();

  private Condition                     _noMoreWrites  = _lock.newCondition();

  private IoFutureListener<WriteFuture> _writeListener = new IoFutureListener<WriteFuture>() {

                                                         public void operationComplete(
                                                             WriteFuture writeRequest)
                                                         {
                                                           try
                                                           {
                                                             _lock.lock();
                                                             _noMoreWrites
                                                                 .signalAll();
                                                           }
                                                           finally
                                                           {
                                                             _lock.unlock();
                                                           }
                                                         }
                                                       };

  public MINASessionInfo(IoSession session)
  {
    super(session);
    session.getService().addListener(new IoServiceListener() {

      @Override
      public void sessionDestroyed(IoSession arg0) throws Exception
      {
        try
        {
          _lock.lock();
          _connected.signalAll();
          _noMoreWrites.signalAll();
        }
        finally
        {
          _lock.unlock();
        }

      }

      @Override
      public void sessionCreated(IoSession arg0) throws Exception
      {

      }

      @Override
      public void serviceIdle(IoService arg0, IdleStatus arg1) throws Exception
      {

      }

      @Override
      public void serviceDeactivated(IoService arg0) throws Exception
      {

      }

      @Override
      public void serviceActivated(IoService arg0) throws Exception
      {

      }
    });
  }

  @Override
  public void write(Object message) throws Exception
  {
    WriteFuture wf = getRawSession().write(message);
    wf.addListener(_writeListener);
  }

  @Override
  public void close() throws Exception
  {
    getRawSession().close(false);
  }

  @Override
  public void writeAndWait(Object message) throws Exception
  {
    getRawSession().write(message).awaitUninterruptibly();
  }

  @Override
  public Object getAttribute(String key)
  {
    return getRawSession().getAttribute(key);
  }

  @Override
  public void setAttribute(String key, Object value)
  {
    getRawSession().setAttribute(key, value);
  }

  @Override
  public void addListener(ISessionListener listener)
  {
    getRawSession().getService().addListener(new IoServiceListener() {

      @Override
      public void sessionDestroyed(IoSession arg0) throws Exception
      {
        MINASessionInfo session = MINASessionInfo.asSessionInfo(arg0);
        listener.closed(session);
        listener.destroyed(session);
      }

      @Override
      public void sessionCreated(IoSession arg0) throws Exception
      {
        MINASessionInfo session = MINASessionInfo.asSessionInfo(arg0);
        listener.created(session);
        listener.opened(session);
      }

      @Override
      public void serviceIdle(IoService arg0, IdleStatus arg1) throws Exception
      {
        // listener.idle(MINASessionInfo.asSessionInfo(arg0));

      }

      @Override
      public void serviceDeactivated(IoService arg0) throws Exception
      {

      }

      @Override
      public void serviceActivated(IoService arg0) throws Exception
      {

      }
    });
  }

  @Override
  public void addFilter(IMessageFilter filter)
  {
    getRawSession().getFilterChain().addBefore("executor",
        filter.getClass().getName(), new IoFilterAdapter() {
          @Override
          public void messageReceived(NextFilter nextFilter, IoSession session,
              Object message) throws Exception
          {
            if (filter.accept(MINASessionInfo.asSessionInfo(session), message))
              super.messageReceived(nextFilter, session, message);
          }
        });
  }

  @Override
  public boolean isClosing()
  {
    return getRawSession().isClosing();
  }

  @Override
  public boolean isConnected()
  {
    return getRawSession().isConnected();
  }

  public <M> void addHandler(Class<M> clazz, IMessageHandler<M> handler)
  {
    DemuxingIoHandler ioh = (DemuxingIoHandler) getRawSession().getService()
        .getHandler();
    ioh.addReceivedMessageHandler(clazz, new MessageHandler<M>() {

      @Override
      public void handleMessage(IoSession arg0, M arg1) throws Exception
      {
        handler.accept(MINASessionInfo.asSessionInfo(arg0), arg1);
      }

    });

  }

  public <M> void removeHandler(Class<M> clazz)
  {
    DemuxingIoHandler ioh = (DemuxingIoHandler) getRawSession().getService()
        .getHandler();
    ioh.removeReceivedMessageHandler(clazz);
  }

  @Override
  public void waitForPendingWrites() throws InterruptedException
  {
    try
    {
      _lock.lock();
      int pendingWrites = 0;
      while (isConnected()
          && (pendingWrites = getRawSession().getScheduledWriteMessages()) != 0)
      {
        if (LOGGER.isDebugEnabled())
          LOGGER.debug(pendingWrites + " writes are waiting");
        _noMoreWrites.await(500, TimeUnit.MILLISECONDS);
      }

      if (LOGGER.isDebugEnabled() && getRawSession() != null)
        LOGGER.debug("connection has "
            + getRawSession().getScheduledWriteMessages() + " remaining");
    }
    finally
    {
      _lock.unlock();
    }

  }

  public void waitForDisconnect() throws InterruptedException
  {
    try
    {
      _lock.lock();
      while (isConnected())
      {
        if (LOGGER.isDebugEnabled())
          LOGGER.debug("Waiting for connection to close");
        _connected.await(500, TimeUnit.MILLISECONDS);
      }

      if (LOGGER.isDebugEnabled()) LOGGER.debug("all sessions disconnected");
    }
    finally
    {
      _lock.unlock();
    }
  }



  @Override
  public void addTransformer(final IMessageTransfromer decorator)
  {
    getRawSession()
        .getFilterChain()
        .addBefore("executor", decorator.getClass().getName(),
            new IoFilterAdapter() {
              @Override
              public void messageReceived(NextFilter nextFilter,
                  IoSession session, Object message) throws Exception
              {
                Collection<?> split = decorator.messageReceived(message);
                for (Object msg : split)
                  super.messageReceived(nextFilter, session, msg);
              }
            });

  }

  @Override
  public void addExceptionHandler(IExceptionHandler handler)
  {
    DemuxingIoHandler ioh = (DemuxingIoHandler) getRawSession().getService()
        .getHandler();
    ioh.addExceptionHandler(Throwable.class, new ExceptionHandler<Throwable>() {

      @Override
      public void exceptionCaught(IoSession arg0, Throwable arg1)
          throws Exception
      {
        handler.exceptionCaught(MINASessionInfo.asSessionInfo(arg0), arg1);
      }

    });

  }

  @Override
  public void flush() throws Exception
  {
    // noop

  }
}

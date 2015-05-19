package org.commonreality.time.impl;

/*
 * default logging
 */
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.time.IAuthoritativeClock;
import org.commonreality.time.IClock;

/**
 * wrapped clock is not currently working as implemented. While it should be
 * routing everything to the delegate, it is also intended to support a local
 * time shift that is not passed on to the delegate.
 * 
 * @author harrison
 */
public class WrappedClock implements IClock
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(WrappedClock.class);

  private final IClock            _delegate;

  public WrappedClock(IClock master)
  {
    _delegate = master;
  }

  public IClock getDelegate()
  {
    return _delegate;
  }

  @Override
  public double getTime()
  {
    return _delegate.getTime();
  }

  @Override
  public Optional<IAuthoritativeClock> getAuthority()
  {
    return _delegate.getAuthority();
  }

  @Override
  public CompletableFuture<Double> waitForChange()
  {
    return _delegate.waitForChange();
  }

  @Override
  public CompletableFuture<Double> waitForTime(double triggerTime)
  {
    return _delegate.waitForTime(triggerTime);
  }

  static public class WrappedAuthority implements IAuthoritativeClock
  {

    IAuthoritativeClock _delegate;

    double              _timeShift = 0;

    public WrappedAuthority(IAuthoritativeClock clock)
    {
      _delegate = clock;
    }

    @Override
    public double getTime()
    {
      return 0;
    }

    @Override
    public Optional<IAuthoritativeClock> getAuthority()
    {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public CompletableFuture<Double> waitForChange()
    {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public CompletableFuture<Double> waitForTime(double triggerTime)
    {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public CompletableFuture<Double> requestAndWaitForChange(Object key)
    {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public CompletableFuture<Double> requestAndWaitForTime(double targetTime,
        Object key)
    {
      return requestAndWaitForTime(targetTime, key);
    }

    @Override
    public double getLocalTimeShift()
    {
      return _timeShift;
    }

    @Override
    public void setLocalTimeShift(double timeShift)
    {
      _timeShift = timeShift;
    }

  }
}

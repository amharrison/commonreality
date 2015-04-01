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

}

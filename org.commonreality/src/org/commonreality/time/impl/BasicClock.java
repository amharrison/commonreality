package org.commonreality.time.impl;

/*
 * default logging
 */
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javolution.util.FastList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.time.IAuthoritativeClock;
import org.commonreality.time.IClock;

public class BasicClock implements IClock
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(BasicClock.class);

  static private final double        PRECISION;

  static
  {
    // 0.0001 1/10th millisecond
    int precisionDigits = 4;
    try
    {
      precisionDigits = Integer.parseInt(System
          .getProperty("commonreality.temporalPrecisionDigits"));
    }
    catch (Exception e)
    {
      precisionDigits = 4;
    }

    PRECISION = Math.round(Math.pow(10, precisionDigits));
  }

  /**
   * constrain our precision.
   * 
   * @param time
   * @return
   */
  static public double constrainPrecision(double time)
  {
    return Math.ceil(time * PRECISION) / PRECISION;
    // return time;
  }

  private volatile double                        _globalTime;                                // unshifted
                                                                                              // time

  private double                                 _timeShift;                                 // time
                                                                                              // correction.

  private Lock                                   _lock                 = new ReentrantLock();

  private Map<CompletableFuture<Double>, Double> _pendingCompletables;

  private IAuthoritativeClock                    _authoritative;

  private double                                 _minimumTimeIncrement = 0.05;

  public BasicClock()
  {
    this(false, 0.05);
  }

  public BasicClock(boolean provideAuthority, final double minimumTimeIncrement)
  {
    _minimumTimeIncrement = minimumTimeIncrement;
    _pendingCompletables = new HashMap<CompletableFuture<Double>, Double>();
    if (provideAuthority) _authoritative = createAuthoritativeClock(this);
  }

  public double getMinimumTimeIncrement()
  {
    return _minimumTimeIncrement;
  }

  /**
   * hook to create an authoritative
   * 
   * @param clock
   * @return
   */
  protected IAuthoritativeClock createAuthoritativeClock(BasicClock clock)
  {
    return new BasicAuthoritativeClock(this);
  }

  static protected void runLocked(Lock lock, Runnable r)
  {
    try
    {
      lock.lock();
      r.run();
    }
    finally
    {
      lock.unlock();
    }
  }

  static protected <T> T runLocked(Lock lock, Callable<T> c)
  {
    T rtn = null;
    try
    {
      lock.lock();
      rtn = c.call();
      return rtn;
    }
    catch (Exception e)
    {
      LOGGER.error(
          "Exception occured while processing callable within clock lock ", e);
      return null;
    }
    finally
    {
      lock.unlock();
    }
  }

  @Override
  public double getTime()
  {
    try
    {
      return runLocked(_lock, () -> {
        return getLocalTime();
      });
    }
    catch (Exception e)
    {
      LOGGER.error(e);
      return Double.NaN;
    }
  }

  @Override
  public Optional<IAuthoritativeClock> getAuthority()
  {
    return Optional.ofNullable(_authoritative);
  }

  @Override
  public CompletableFuture<Double> waitForChange()
  {
    return newFuture(Double.NaN);
  }

  @Override
  public CompletableFuture<Double> waitForTime(double triggerTime)
  {
    double now = getTime();
    boolean hasPassed = now >= triggerTime;

    CompletableFuture<Double> rtn = newFuture(BasicClock
        .constrainPrecision(triggerTime));

    if (hasPassed)
    {
      if (LOGGER.isDebugEnabled())
        LOGGER.debug(String.format("time has already elapsed, forcing firing"));
      fireExpiredFutures(now);
    }

    return rtn;
  }

  /**
   * runs unlocked.
   * 
   * @param timeShift
   */
  protected void setTimeShift(double timeShift)
  {
    _timeShift = timeShift;
  }

  /**
   * returns w/o locking
   * 
   * @return
   */
  protected double getTimeShift()
  {
    return _timeShift;
  }

  /**
   * returns the local time w/o locking
   * 
   * @return
   */
  protected double getLocalTime()
  {
    return _globalTime + _timeShift;
  }

  /**
   * return global w/o locking.
   * 
   * @return
   */
  protected double getGlobalTime()
  {
    return _globalTime;
  }

  protected Lock getLock()
  {
    return _lock;
  }

  /**
   * create a new future that will be completed at or after trigger time
   * (Double.NaN if any change)
   * 
   * @param triggerTime
   * @return
   */
  protected CompletableFuture<Double> newFuture(final double triggerTime)
  {
    final CompletableFuture<Double> rtn = new CompletableFuture<Double>();

    runLocked(_lock, () -> {
          if (LOGGER.isDebugEnabled())
            LOGGER.debug(String.format("[%d].waitFor(%.4f)", rtn.hashCode(),
                triggerTime));
      _pendingCompletables.put(rtn, triggerTime);
    });

    return rtn;
  }

  /**
   * set the local time (indirectly setting global time) and fire completion.
   * Functionally the same as {@link #setGlobalTime(double)}
   * 
   * @param currentLocalTime
   */
  protected void setLocalTime(final double currentLocalTime)
  {
    double localTime = 0;
    try
    {
      localTime = runLocked(
          _lock,
          () -> {
            _globalTime = currentLocalTime - _timeShift;
            if (LOGGER.isDebugEnabled())
              LOGGER.debug(String.format("Time[%.2f, %.2f, %.2f]", _globalTime,
                  currentLocalTime, _timeShift));
            return currentLocalTime;
          });
    }
    catch (Exception e)
    {
      LOGGER.error(e);
      localTime = currentLocalTime;
    }

    fireExpiredFutures(localTime);
  }

  /**
   * set the global time value, firing any pending completables.
   * 
   * @param globalTime
   */
  protected void setGlobalTime(final double globalTime)
  {
    double localTime = 0;
    try
    {
      localTime = runLocked(
          _lock,
          () -> {
            _globalTime = globalTime;
            if (LOGGER.isDebugEnabled())
              LOGGER.debug(String.format("Time[%.4f, %.4f, %.4f]", _globalTime,
                  _globalTime + _timeShift, _timeShift));
            return getLocalTime();
          });
    }
    catch (Exception e)
    {
      LOGGER.error(e);
      localTime = getLocalTime(); // just in case.
    }

    fireExpiredFutures(localTime);
  }

  protected void fireExpiredFutures(double localTime)
  {
    FastList<CompletableFuture<Double>> container = FastList.newInstance();

    // get and remove
    removeExpiredCompletables(localTime, container);

    if (LOGGER.isDebugEnabled())
      LOGGER.debug(String.format("Notifying %d futures", container.size()));

    // fire and forget
    for (CompletableFuture<Double> future : container)
      future.complete(localTime);

    FastList.recycle(container);
  }

  protected Collection<CompletableFuture<Double>> removeExpiredCompletables(
      final double now, final Collection<CompletableFuture<Double>> container)
  {
    runLocked(
        _lock,
        () -> {
          if (LOGGER.isDebugEnabled())
            LOGGER.debug(String.format("Collecting expired futures after %.4f",
                now));

          Iterator<Map.Entry<CompletableFuture<Double>, Double>> itr = _pendingCompletables
              .entrySet().iterator();
          while (itr.hasNext())
          {
            Map.Entry<CompletableFuture<Double>, Double> entry = itr.next();
            double trigger = entry.getValue();
            if (Double.isNaN(trigger) || trigger <= now)
            {
              CompletableFuture<Double> future = entry.getKey();
              if (LOGGER.isDebugEnabled())
                LOGGER.debug(String.format("[%d].trigger = %.4f",
                    future.hashCode(), trigger));
              container.add(future);
              itr.remove();
            }
          }
        });
    return container;
  }

  /**
   * A starter authoritative for the basic clock. One can extend this by merely
   * adapting {@link #requestTimeChange(double)}
   * 
   * @author harrison
   */
  public static class BasicAuthoritativeClock extends
      AbstractAuthoritativeClock
  {

    public BasicAuthoritativeClock(BasicClock clock)
    {
      super(clock);
    }

    @Override
    public BasicClock getDelegate()
    {
      return (BasicClock) super.getDelegate();
    }

    @Override
    public void setLocalTimeShift(final double timeShift)
    {
      BasicClock bc = getDelegate();
      BasicClock.runLocked(bc._lock, () -> {
        bc.setTimeShift(timeShift);
      });
    }

    /**
     * request that the time be changed. Returns true if we can proceed and use
     * the targetTime value. This impl merely returns true.
     * 
     * @param targetTime
     * @return
     */
    protected boolean requestTimeChange(double targetTime, Object key)
    {
      return true;
    }

    @Override
    public CompletableFuture<Double> requestAndWaitForTime(double targetTime,
        final Object key)
    {
      targetTime = BasicClock.constrainPrecision(targetTime);
      BasicClock bc = getDelegate();
      CompletableFuture<Double> rtn = bc.newFuture(targetTime);
      if (requestTimeChange(targetTime, key)) bc.setLocalTime(targetTime);
      return rtn;
    }

    @Override
    public CompletableFuture<Double> requestAndWaitForChange(final Object key)
    {
      BasicClock bc = getDelegate();
      CompletableFuture<Double> rtn = bc.newFuture(Double.NaN);
      if (requestTimeChange(Double.NaN, key))
        bc.setLocalTime(getTime() + bc._minimumTimeIncrement);
      return rtn;
    }

    @Override
    public double getLocalTimeShift()
    {
      final BasicClock bc = getDelegate();
      return BasicClock.runLocked(bc._lock, () -> {
        return bc.getTimeShift();
      });
    }
  }
}

/*
 * Created on Feb 25, 2007 Copyright (C) 2001-6, Anthony Harrison anh23@pitt.edu
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
package org.commonreality.time.impl;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.time.IClock;
import org.commonreality.time.ISetableClock;

/**
 * Basic Clock from which others are built. The clock supports shifting local
 * time, constrainedPrecision, and time shift detection.</br> Constrained
 * precision is implemented as a defensive position against floating (double)
 * point resolution errors. By default, the precision is 4 digits (0.0001s). The
 * runtime property "commonreality.temporalPrecisionDigits" can be set to the
 * number of digits (4). </br>
 * 
 * @author developer
 */
public class BasicClock implements IClock, ISetableClock
{
  /**
   * logger definition
   */
  static private final Log    LOGGER                   = LogFactory
                                                           .getLog(BasicClock.class);

  protected Lock              _lock                    = new ReentrantLock();

  protected Condition         _timeChangeCondition     = _lock.newCondition();

  private volatile double     _globalTime              = -0.001;

  private double              _timeShift;

  private boolean             _ignoreTimeDiscrepencies = false;

  private WaitFor             _waitFor;

  private WaitFor             _waitForAny;

  private long                _defaultWaitTime         = 100;

  static private double       _timeSlipTolerance       = 0.025;

  static private final double PRECISION;

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

    try
    {
      _timeSlipTolerance = Double.parseDouble(System
          .getProperty("commonreality.timeSlipTolerance"));
    }
    catch (Exception e)
    {
      _timeSlipTolerance = 0.025;
    }
  }

  static public double getTimeSlipTolerance()
  {
    return _timeSlipTolerance;
  }

  public BasicClock()
  {
    _waitFor = createWaitForTime();
    _waitForAny = createWaitForAny();
  }

  /**
   * how much can the desired time differ from the target time before a warning
   * is issued.
   * 
   * @param tolerance
   */
  public void setTimeSlipTolerance(double tolerance)
  {
    _timeSlipTolerance = tolerance;
  }

  protected long getDefaultWaitTime()
  {
    return _defaultWaitTime;
  }

  protected void setDefaultWaitTime(long waitTime)
  {
    _defaultWaitTime = waitTime;
  }

  protected WaitFor createWaitForTime()
  {
    return new WaitFor();
  }

  protected WaitFor createWaitForAny()
  {
    return new WaitFor() {
      @Override
      public boolean shouldWait(double currentTime)
      {
        return Math.abs(getWaitForTime() - currentTime) <= getEpsilon();
      }
    };
  }

  protected WaitFor getWaitForAny()
  {
    return _waitForAny;
  }

  protected WaitFor getWaitForTime()
  {
    return _waitFor;
  }

  /**
   * @see org.commonreality.time.IClock#getTime()
   */
  public double getTime()
  {
    try
    {
      _lock.lock();
      return _globalTime + getTimeShift();
    }
    finally
    {
      _lock.unlock();
    }
  }

  /**
   * are we temporarily ignoring time discrepencies - useful for the clock
   * owners when a simulation is reset
   * 
   * @return
   */
  public boolean isIgnoringDiscrepencies()
  {
    return _ignoreTimeDiscrepencies;
  }

  public void setIgnoreDiscrepencies(boolean ignore)
  {
    _ignoreTimeDiscrepencies = ignore;
  }

  /**
   * @see org.commonreality.time.IClock#waitForChange()
   */
  public double waitForChange() throws InterruptedException
  {
    double now = getTime();
    WaitFor any = getWaitForAny();
    any.setWaitForTime(now);

    return await(any, Double.NaN, getDefaultWaitTime());
  }

  /**
   * @see org.commonreality.time.IClock#waitForTime(double)
   * @return localTime
   */
  public double waitForTime(double localTime) throws InterruptedException
  {
    double globalTime = constrainPrecision(localTime) - getTimeShift();
    WaitFor wait = getWaitForTime();
    wait.setWaitForTime(globalTime);
    double rtnLocal = await(wait, globalTime, getDefaultWaitTime())
        + getTimeShift();

    if (localTime < rtnLocal && !isIgnoringDiscrepencies()
        && Math.abs(rtnLocal - localTime) >= _timeSlipTolerance)
      if (LOGGER.isWarnEnabled())
        LOGGER.warn(rtnLocal - localTime + " time slippage detected, wanted "
            + localTime + " got " + rtnLocal);

    return rtnLocal;
  }

  /**
   * @param targetGlobalTime
   *          is global time (unshifted)
   * @return global Time
   * @param maxWait
   *          ms to wait (0 to wait indef)
   * @throws InterruptedException
   */
  private double await(IClockWaiter waiter, double targetGlobalTime,
      long maxWait) throws InterruptedException
  {
    // targetTime = constrainPrecision(targetTime);
    double globalTime = getTime() - getTimeShift();
    while (waiter.shouldWait(globalTime))
    {
      if (!requestTime(targetGlobalTime)) try
      {
        _lock.lock();
        _timeChangeCondition.await(maxWait, TimeUnit.MILLISECONDS);
      }
      finally
      {
        _lock.unlock();
      }
      globalTime = getTime() - getTimeShift();
    }

    return getTime() - getTimeShift();
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

  /**
   * set the time and signal (this is shifted time)
   * 
   * @param time
   */
  public double setTime(double localTime)
  {
    localTime = constrainPrecision(localTime);

    double lastLocal = getTime();

    if (localTime < lastLocal && !isIgnoringDiscrepencies())
      if (LOGGER.isWarnEnabled())
        LOGGER.warn("Attempting to roll clock back from " + lastLocal + " to "
            + localTime);

    return setTimeInternal(localTime);
  }

  /**
   * within the waitForTime() or waitForChange(), when the thread should block,
   * this is called before the actual block, allowing extenders to send out
   * requests for time updates, if necessary. Currently noop, returning false.
   * this can be a blocking event as it is outside of the clock's lock.
   * 
   * @param globalRequestedTime
   *          NaN if waitForChange was called
   * @return true if the wait should be skipped, that is the requestTime was
   *         immediately successful. default: false
   * @throws InterruptedException
   */
  protected boolean requestTime(double globalRequestedTime)
      throws InterruptedException
  {
    return false;
  }

  /**
   * actually set the time
   * 
   * @param localTime
   */
  protected double setTimeInternal(double localTime)
  {
    try
    {
      _lock.lock();

      _globalTime = localTime - getTimeShift();

      if (LOGGER.isDebugEnabled())
        LOGGER.debug(String.format(
            "Signalling local time (%.5f) from global time (%.5f) shift(%.5f)",
            localTime, _globalTime, getTimeShift()));

      // always signal.. just in case
      _timeChangeCondition.signalAll();

      return _globalTime;
    }
    finally
    {
      _lock.unlock();
    }
  }

  public double getTimeShift()
  {
    return _timeShift;
  }

  public void setTimeShift(double shift)
  {
    _timeShift = constrainPrecision(shift);
    if (LOGGER.isDebugEnabled())
      LOGGER.debug(String.format("Set timeshift %.5f from %.5f", _timeShift,
          shift));
  }

  /**
   * a closure interface that is used in #await(IClockWaiter, log) to specify
   * when the await should be retured from
   * 
   * @author harrison
   */
  static public interface IClockWaiter
  {
    /**
     * return true while the clock should be blocked on
     * 
     * @param currentTime
     *          TODO
     * @return
     */
    public boolean shouldWait(double currentTime);
  }

  /**
   * default impl of IClockWaiter that allows you to specify an exit time
   * 
   * @author harrison
   */
  public class WaitFor implements IClockWaiter
  {

    private ThreadLocal<Double> _timeToWait = new ThreadLocal<Double>();

    private double              _epsilon    = 1 / (10 * PRECISION);

    /**
     * epislon used for delta calculations, currently 0.0001
     * 
     * @return
     */
    protected double getEpsilon()
    {
      // currently the rounded size of the constraint
      return _epsilon;
    }

    public void setWaitForTime(double time)
    {
      _timeToWait.set(time);
    }

    protected double getWaitForTime()
    {
      return _timeToWait.get();
    }

    public boolean shouldWait(double currentTime)
    {
      double delta = getWaitForTime() - currentTime;
      boolean shouldWait = delta >= getEpsilon();

      /*
       * epsilon gap. current time is close enough to target, but still less
       * than, meaning that while we will clear the clock block, events still
       * won't fire. In this case, instead of asking for a later time, we will
       * change our timeShift locally to put us at the current time. </br>
       */
      if (!shouldWait && delta > 0)
      {
        if (LOGGER.isWarnEnabled())
          LOGGER.warn(String.format("Adjusting time shift by %.5f", delta));
        setTimeShift(getTimeShift() + delta);
      }

      return shouldWait;
    }

  }
}

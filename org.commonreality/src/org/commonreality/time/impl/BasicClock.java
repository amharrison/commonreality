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
 * @author developer
 */
public class BasicClock implements IClock, ISetableClock
{
  /**
   * logger definition
   */
  static private final Log LOGGER                   = LogFactory
                                                        .getLog(BasicClock.class);

  protected Lock           _lock                    = new ReentrantLock();

  private Condition        _timeChangeCondition     = _lock.newCondition();

  private volatile double  _currentTime             = -0.001;

  private double           _timeShift;

  private boolean          _ignoreTimeDiscrepencies = false;

  private WaitFor          _waitFor;

  private WaitFor          _waitForAny;

  private long             _defaultWaitTime         = 0;

  static private double    _timeSlipTolerance       = 0.025;

  static
  {
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
        return currentTime == getWaitForTime();
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
      return _currentTime + getTimeShift();
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

    // try
    // {
    // _lock.lock();
    // while (now == getTime())
    // await(0);
    // }
    // finally
    // {
    // _lock.unlock();
    // }
    return await(any, getDefaultWaitTime());
  }

  /**
   * @see org.commonreality.time.IClock#waitForTime(double)
   */
  public double waitForTime(double time) throws InterruptedException
  {
    // time -= getTimeShift();
    WaitFor wait = getWaitForTime();
    wait.setWaitForTime(time);
    double rtn = await(wait, getDefaultWaitTime());

    // try
    // {
    // _lock.lock();
    // while (getTime() < time)
    // await(0);
    // }
    // finally
    // {
    // _lock.unlock();
    // }

    // double rtn = getTime();

    if (time < rtn && !isIgnoringDiscrepencies()
        && Math.abs(rtn - time) >= _timeSlipTolerance)
      if (LOGGER.isWarnEnabled())
        LOGGER.warn(rtn - time + " time slippage detected, wanted " + time
            + " got " + rtn);

    return rtn;
  }

  /**
   * wait on the signal..
   * 
   * @param maxWait
   *          ms to wait (0 to wait indef)
   * @throws InterruptedException
   */
  public double await(IClockWaiter waiter, long maxWait)
      throws InterruptedException
  {
    try
    {
      _lock.lock();
      double now = getTime();
      while (waiter.shouldWait(now))
      {
        if (maxWait <= 0)
          _timeChangeCondition.await();
        else
          _timeChangeCondition.await(maxWait, TimeUnit.MILLISECONDS);
        now = getTime();
      }
      return getTime();
    }
    finally
    {
      _lock.unlock();
    }
  }

  /**
   * set the time and signal (this is shifted time)
   * 
   * @param time
   */
  public double setTime(double time)
  {
    try
    {
      _lock.lock();

      double last = getTime();

      if (time < last && !isIgnoringDiscrepencies())
        if (LOGGER.isWarnEnabled())
          LOGGER.warn("Rolling clock back from " + last + " to " + time);

      _currentTime = time - getTimeShift();

      if (LOGGER.isDebugEnabled()) LOGGER.debug("Signalling time=" + time);

      // always signal.. just in case
      _timeChangeCondition.signalAll();
      return getTime();
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
    _timeShift = shift;
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
  static public class WaitFor implements IClockWaiter
  {

    private ThreadLocal<Double> _timeToWait = new ThreadLocal<Double>();

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
      return getWaitForTime() < currentTime;
    }

  }
}

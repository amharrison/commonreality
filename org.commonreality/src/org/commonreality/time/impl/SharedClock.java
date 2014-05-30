/*
 * Created on May 14, 2007 Copyright (C) 2001-2007, Anthony Harrison
 * anh23@pitt.edu (jactr.org) This library is free software; you can
 * redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version. This library is
 * distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details. You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.commonreality.time.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author developer
 */
public class SharedClock extends OwnedClock<Thread>
{
  /**
   * logger definition
   */
  static private final Log LOGGER = LogFactory.getLog(SharedClock.class);

  @Override
  protected WaitFor createWaitForTime()
  {
    return new WaitFor() {
      @Override
      public boolean shouldWait(double currentTime)
      {
        return super.shouldWait(currentTime);
        // double targetTime = getWaitForTime();
        // boolean shouldWait = Double.isInfinite(currentTime)
        // || targetTime - currentTime > getEpsilon();
        //
        // if (shouldWait)
        // {
        // Thread current = Thread.currentThread();
        // boolean isOwner = isOwner(current);
        // /*
        // * attempt to set the time
        // */
        // if (isOwner && targetTime <= setTime(current, targetTime))
        // return false;
        // }
        //
        // return shouldWait;
      }
    };
  }

  @Override
  protected WaitFor createWaitForAny()
  {
    return new WaitFor() {
      @Override
      public boolean shouldWait(double currentTime)
      {
        return super.shouldWait(currentTime);

        // double targetTime = getWaitForTime();
        // boolean shouldWait = Double.isInfinite(currentTime)
        // || Math.abs(targetTime - currentTime) <= getEpsilon();
        //
        // if (shouldWait)
        // {
        // Thread current = Thread.currentThread();
        // boolean isOwner = isOwner(current);
        // /*
        // * attempt to set the time
        // */
        //
        // if (isOwner && targetTime != setTime(current, Double.NaN))
        // return false;
        // }
        //
        // return shouldWait;
      }
    };
  }

  /**
   * since this might actually set the time to the requestedTime, the return
   * value varies. Basically, if the requestedTime ends up being the lowest bid,
   * we return true
   * 
   * @throws InterruptedException
   */
  @Override
  protected boolean requestTime(double requestedTime)
      throws InterruptedException
  {
    Thread current = Thread.currentThread();
    boolean isOwner = isOwner(current);
    boolean rtn = false;
    /*
     * attempt to set the time.If successful, we report back that there should
     * be no blocking on return. if requestedTime is NaN, this will be an any.
     */
    if (isOwner)
    {
      double returnedTime = 0;
      if (Double.isNaN(requestedTime))
      {
        returnedTime = setTime(current, Double.NaN);
        rtn = requestedTime != returnedTime;
      }
      else
      {
        returnedTime = setTime(current, requestedTime);
        rtn = requestedTime <= returnedTime;
      }
    }

    /*
     * normally, block on return
     */
    return rtn;
  }

  // @Override
  // public double waitForTime(double time) throws InterruptedException
  // {
  // // Thread current = Thread.currentThread();
  // // boolean isOwner = isOwner(current);
  // // try
  // // {
  // // _lock.lock();
  // // while (getTime() < time)
  // // {
  // // if (isOwner)
  // // {
  // // if (time <= setTime(current, time))
  // // {
  // // if (LOGGER.isDebugEnabled()) LOGGER.debug("its my time");
  // // break;
  // // }
  // // }
  // // await(0);
  // // if (LOGGER.isDebugEnabled()) LOGGER.debug("awoke");
  // // }
  // // }
  // // finally
  // // {
  // // _lock.unlock();
  // // }
  //
  // // time -= getTimeShift();
  // WaitFor wait = getWaitForTime();
  // wait.setWaitForTime(time);
  // double rtn = await(wait, 0);
  //
  // if (time < rtn)
  // if (LOGGER.isWarnEnabled())
  // LOGGER.warn("Time slippage detected, wanted " + time + " got " + rtn);
  //
  // return rtn;
  // }
  //
  // @Override
  // public double waitForChange() throws InterruptedException
  // {
  // // Thread current = Thread.currentThread();
  // // boolean isOwner = isOwner(current);
  // // double now = getTime();
  // // try
  // // {
  // // _lock.lock();
  // // while (now == getTime())
  // // {
  // // if (isOwner) if (now != setTime(current, Double.NaN)) break;
  // // await(0);
  // // }
  // // }
  // // finally
  // // {
  // // _lock.unlock();
  // // }
  // // return getTime();
  // WaitFor any = getWaitForAny();
  // any.setWaitForTime(getTime());
  // return await(any, 0);
  // }
}

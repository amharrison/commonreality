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
import org.commonreality.time.IClock;

/**
 * @author developer
 */
public class RealTimeClock implements IClock
{
  /**
   * logger definition
   */
  static private final Log LOGGER             = LogFactory
                                                  .getLog(RealTimeClock.class);

  long                     _startTime         = System.currentTimeMillis();

  double                   _timeShift;


  /**
   * @see org.commonreality.time.IClock#getTime()
   */
  public double getTime()
  {
    return (System.currentTimeMillis() - _startTime) / 1000.0 + getTimeShift();
  }

  /**
   * @see org.commonreality.time.IClock#waitForChange()
   */
  public double waitForChange() throws InterruptedException
  {
    return getTime();
  }

  /**
   * @see org.commonreality.time.IClock#waitForTime(double)
   */
  public double waitForTime(double time) throws InterruptedException
  {
    double secondsToWait = time - getTime();
    if (secondsToWait > 0) Thread.sleep((long) (secondsToWait * 1000.0));
    double rtn = getTime();

    if (Math.abs(time - rtn) >= BasicClock.getTimeSlipTolerance())
      if (LOGGER.isWarnEnabled())
        LOGGER.warn(String.format(
            "Time slippage detected, wanted %.3f got %.3f", time, rtn));

    return rtn;
  }

  public double getTimeShift()
  {
    return _timeShift;
  }

  public void setTimeShift(double shift)
  {
    _timeShift = shift;
  }

}

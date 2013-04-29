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
package org.commonreality.time.impl.net;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.message.command.time.ITimeCommand;
import org.commonreality.time.IClock;
import org.commonreality.time.impl.BasicClock;

/**
 * network slave clock
 * 
 * @author developer
 */
public class NetworkedSlaveClock implements IClock, INetworkedClock
{
  /**
   * logger definition
   */
  static private final Log LOGGER = LogFactory
                                      .getLog(NetworkedSlaveClock.class);

  private BasicClock       _clock = new BasicClock();

  private ITimeCommand     _currentTimeCommand;

  /**
   * @see org.commonreality.time.IClock#getTime()
   */
  public double getTime()
  {
    return _clock.getTime();
  }

  /**
   * @see org.commonreality.time.IClock#waitForChange()
   */
  public double waitForChange() throws InterruptedException
  {
    return _clock.waitForChange();
  }

  /**
   * @see org.commonreality.time.IClock#waitForTime(double)
   */
  public double waitForTime(double time) throws InterruptedException
  {
    return _clock.waitForTime(time+_clock.getTimeShift());
  }

  public void setCurrentTimeCommand(ITimeCommand timeCommand)
  {
    _currentTimeCommand = timeCommand;
    /*
     * network time is unshifted, so we have to shift
     */
    _clock.setTime(_currentTimeCommand.getTime()+_clock.getTimeShift());
  }

  public double getTimeShift()
  {
    return _clock.getTimeShift();
  }

  public void setTimeShift(double shift)
  {
    _clock.setTimeShift(shift);
  }
}

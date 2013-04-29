package org.commonreality.time.impl;

/*
 * default logging
 */
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.time.IClock;

public class WrappedClock implements IClock
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(WrappedClock.class);

  
  final private IClock _clock;
  private double _timeShift = 0;
  
  public WrappedClock(IClock clock)
  {
    _clock = clock;
  }
  
  public double getTime()
  {
    return _clock.getTime()+getTimeShift();
  }

  public double getTimeShift()
  {
    return _timeShift;
  }

  public void setTimeShift(double shift)
  {
   _timeShift = shift;
  }

  public double waitForChange() throws InterruptedException
  {
    return _timeShift + _clock.waitForChange();
  }

  public double waitForTime(double time) throws InterruptedException
  {
    return _timeShift + _clock.waitForTime(time - getTimeShift());
  }

}

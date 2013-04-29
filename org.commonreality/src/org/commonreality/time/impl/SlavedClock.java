package org.commonreality.time.impl;

/*
 * default logging
 */
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.time.IClock;

public class SlavedClock extends WrappedClock
{

  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(SlavedClock.class);

  public SlavedClock(IClock clock)
  {
    super(clock);
  }

  @Override
  public double waitForTime(double time) throws InterruptedException
  {
    while (time > getTime())
      waitForChange();
    return getTime();
  }
}

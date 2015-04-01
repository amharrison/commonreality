package org.commonreality.time.impl;

/*
 * default logging
 */
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.time.IAuthoritativeClock;

/**
 * General networked clock support. The code to send the network message for a
 * time request is generalized as a {@link Consumer} that accepts a double. When
 * your network code receives an update message, call
 * {@link NetworkedAuthoritativeClock#timeChangeReceived(double)}
 * 
 * @author harrison
 */
public class NetworkedClock extends BasicClock
{
  /**
   * Logger definition
   */
  static private final transient Log               LOGGER = LogFactory
                                                              .getLog(NetworkedClock.class);

  final private BiConsumer<Double, NetworkedClock> _networkSendCommand;

  public NetworkedClock(double minimumTimeIncrement,
      BiConsumer<Double, NetworkedClock> networkSendCommand)
  {
    super(true, minimumTimeIncrement);
    _networkSendCommand = networkSendCommand;
  }

  @Override
  protected IAuthoritativeClock createAuthoritativeClock(BasicClock clock)
  {
    // return ours instead
    return new NetworkedAuthoritativeClock(this);
  }

  /**
   * called by networked auth when we receive a clock update
   * 
   * @param targetTime
   */
  protected void timeChangeReceived(double globalTime)
  {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug(String.format("Time update received : %.4f", globalTime));
    // set global time and notify
    setGlobalTime(globalTime);
  }

  static public class NetworkedAuthoritativeClock extends
      BasicAuthoritativeClock
  {

    public NetworkedAuthoritativeClock(BasicClock clock)
    {
      super(clock);
    }

    /**
     * called by the networking code. global time
     * 
     * @param globalTime
     */
    public void timeChangeReceived(double globalTime)
    {
      ((NetworkedClock) getDelegate()).timeChangeReceived(globalTime);
    }

    /**
     * always return false so that we don't fire the update until after we get
     * confirmation.
     */
    @Override
    protected boolean requestTimeChange(double targetTime, Object key)
    {
      // make the necessary calls
      try
      {
        NetworkedClock delegate = (NetworkedClock) getDelegate();
        if (LOGGER.isDebugEnabled())
          LOGGER.debug(String.format("Sending time request %.4f", targetTime));

        delegate._networkSendCommand.accept(targetTime, delegate);
      }
      catch (Exception e)
      {
        LOGGER.error("Failed to request time update ", e);
      }
      return false;
    }

  }
}

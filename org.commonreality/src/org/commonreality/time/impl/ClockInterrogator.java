package org.commonreality.time.impl;

/*
 * default logging
 */
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.agents.IAgent;
import org.commonreality.participant.IParticipant;
import org.commonreality.reality.CommonReality;
import org.commonreality.reality.IReality;
import org.commonreality.sensors.ISensor;
import org.commonreality.time.IClock;

public class ClockInterrogator
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(ClockInterrogator.class);

  static public String getAllClockDetails()
  {
    StringBuilder sb = new StringBuilder();

    IReality reality = CommonReality.getReality();
    if (reality != null) sb.append(getClockDetails(reality)).append("\n");

    for (ISensor sensor : CommonReality.getSensors())
      sb.append(getClockDetails(sensor)).append("\n");
    for (IAgent agent : CommonReality.getAgents())
      sb.append(getClockDetails(agent)).append("\n");

    return sb.toString();
  }

  static protected String getClockDetails(IParticipant participant)
  {
    StringBuilder sb = new StringBuilder(participant.getIdentifier().toString());
    IClock clock = participant.getClock();
    sb.append(" [").append(clock.getClass().getSimpleName()).append("] ");
    if (clock instanceof BasicClock)
    {
      BasicClock bc = (BasicClock) clock;
      sb.append(String.format(" lock[%d] ", bc.getLock().hashCode()));
      sb.append(String.format(
          "lastUpdate: %.4f updateTime=%d lastRequest: %.4f requestTime=%d",
          bc.getLastUpdateLocalTime(), bc.getLastUpdateSystemTime(),
          bc.getLastRequestLocalTime(), bc.getLastRequestSystemTime()));
    }

    return sb.toString();
  }
}

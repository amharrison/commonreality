package org.commonreality.agents;

/*
 * default logging
 */
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DumbAgent extends AbstractAgent
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(DumbAgent.class);

  public DumbAgent()
  {
  }

  @Override
  public String getName()
  {
    return "dumb";
  }

}

package org.commonreality.executor;

/*
 * default logging
 */
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ThreadNamer implements Runnable
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(ThreadNamer.class);

  private String                     _name;

  public ThreadNamer(String name)
  {
    _name = name;
  }

  public void run()
  {
    try
    {
      Thread.currentThread().setName(_name);
    }
    catch (SecurityException se)
    {

    }
  }

}

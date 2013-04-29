package org.commonreality.mina.filter;

/*
 * default logging
 */
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;

public class LogFilter extends IoFilterAdapter
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(LogFilter.class);
  
  

  public void filterWrite(NextFilter nextFilter, IoSession session,
      WriteRequest writeRequest) throws Exception
  {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("Writing " + writeRequest.getMessage() + " to " + session);
    
    
    super.filterWrite(nextFilter, session, writeRequest);
  }
}

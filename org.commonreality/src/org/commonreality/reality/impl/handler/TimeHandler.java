/*
 * Created on May 10, 2007 Copyright (C) 2001-6, Anthony Harrison anh23@pitt.edu
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
package org.commonreality.reality.impl.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.handler.demux.MessageHandler;
import org.commonreality.identifier.IIdentifier;
import org.commonreality.message.impl.BaseAcknowledgementMessage;
import org.commonreality.message.request.time.IRequestTime;
import org.commonreality.reality.IReality;
import org.commonreality.time.IAuthoritativeClock;
import org.commonreality.time.IClock;

/**
 * @author developer
 */
public class TimeHandler implements MessageHandler<IRequestTime>
{
  /**
   * logger definition
   */
  static private final Log LOGGER = LogFactory.getLog(TimeHandler.class);

  private IReality         _reality;

  public TimeHandler(IReality reality)
  {
    _reality = reality;
  }


  public void handleMessage(IoSession arg0, IRequestTime timeRequest) throws Exception
  {
    IIdentifier id = timeRequest.getSource();

    /*
     * ack out of good form
     */
    arg0.write(new BaseAcknowledgementMessage(id, timeRequest.getMessageId()));

    double when = timeRequest.getTime();
    if (LOGGER.isDebugEnabled())
      LOGGER.debug(id + " wants time to be " + when);
    try
    {
      IClock clock = _reality.getClock();
      IAuthoritativeClock auth = clock.getAuthority().get();

      auth.requestAndWaitForTime(when, id);
    }
    catch(IllegalArgumentException iae)
    {
      if(LOGGER.isInfoEnabled())
        LOGGER.info(id+" was not a recognized clock owner, perhaps it was received after shutdown commenced? ",iae);
    }
  }

}

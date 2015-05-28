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
package org.commonreality.sensors.xml.tasks;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.identifier.IIdentifier;
import org.commonreality.net.message.IMessage;
import org.commonreality.sensors.xml.XMLProcessor;
import org.commonreality.sensors.xml.XMLSensor;
import org.commonreality.time.IClock;
import org.w3c.dom.Element;

/**
 * @author developer
 */
public class ProcessTimeFrame implements Runnable
{
  /**
   * logger definition
   */
  static private final Log LOGGER = LogFactory.getLog(ProcessTimeFrame.class);

  private Element          _timeFrame;

  private XMLProcessor     _processor;

  private double           _lastTime;

  private Thread           _thread;

  public ProcessTimeFrame(XMLProcessor processor)
  {
    _processor = processor;
  }

  public void setTimeElement(Element timeFrame)
  {
    _timeFrame = timeFrame;
  }

  public void interrupt()
  {
    if(_thread!=null)
      _thread.interrupt();
    
  }
  /**
   * @see java.lang.Runnable#run()
   */
  public void run()
  {
    try
    {
      _thread = Thread.currentThread();
      
      XMLSensor sensor = _processor.getSensor();
      IClock clock = sensor.getClock();

      Collection<IIdentifier> agents = sensor.getInterfacedAgents();

      if (agents.size() == 0)
        if (LOGGER.isWarnEnabled())
          LOGGER.warn("XMLSensor is unaware of any agents!");

      double forTime = _processor.getTime(_timeFrame);

      if (_lastTime == forTime)
        if (LOGGER.isWarnEnabled())
          LOGGER
              .warn("Last time frame is the same as the current time frame ("
                  + forTime
                  + "). If identifiers are the same, there may be collisions and/or lost events!");

      if (LOGGER.isDebugEnabled())
        LOGGER.debug(hashCode() + " Processing XML data for " + forTime);

      Map<IIdentifier, Collection<IMessage>> data = new HashMap<IIdentifier, Collection<IMessage>>();

      for (IIdentifier agent : agents)
        data.put(agent, _processor.processFrame(agent, _timeFrame, true));


      if (LOGGER.isDebugEnabled()) LOGGER.debug("Waiting for time=" + forTime);

      // with the new clock framework we can make this request and then resume
      // processing.
      forTime = clock.getAuthority().get().requestAndWaitForTime(forTime, null)
          .get();

      if (LOGGER.isDebugEnabled())
        LOGGER.debug(hashCode() + " Sending XML Data @ " + forTime);
      
      for (Map.Entry<IIdentifier, Collection<IMessage>> entry : data.entrySet())
         _processor.sendData(entry.getKey(), entry.getValue());

      _lastTime = forTime;

      /*
       * set up another time frame to be processed
       */
      sensor.queueNextFrame();
    }
    catch (InterruptedException e)
    {
      LOGGER.warn("Interrupted, expecting termination ", e);
    }
    catch (ExecutionException ee)
    {
      LOGGER.error(ee);
    }
    finally
    {
      _thread = null;
    }
  }
}


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

import java.util.concurrent.ExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.sensors.xml.XMLSensor;

/**
 * @author developer
 */
public class NoOp implements Runnable
{
  /**
   * logger definition
   */
  static private final Log LOGGER = LogFactory.getLog(NoOp.class);

  private XMLSensor        _sensor;

  private Thread           _thread;

  public NoOp(XMLSensor sensor)
  {
    _sensor = sensor;
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
      
      if (LOGGER.isDebugEnabled()) LOGGER.debug("waiting for time change");
      
      _sensor.getClock().getAuthority().get().requestAndWaitForChange(null)
          .get();

      _sensor.queueNextFrame();
    }
    catch (InterruptedException e)
    {
      LOGGER.debug("interrupted ", e);
    }
    catch (ExecutionException ee)
    {
      LOGGER.error("Execution exception ", ee);
    }
    finally
    {
      _thread = null;
    }

  }

}

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
package org.commonreality.time.impl.net;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.message.command.time.TimeCommand;
import org.commonreality.reality.IReality;
import org.commonreality.time.impl.MasterClock;

/**
 * @author developer
 */
public class NetworkedMasterClock extends MasterClock
{
  /**
   * logger definition
   */
  static private final Log LOGGER = LogFactory
                                      .getLog(NetworkedMasterClock.class);

  IReality                 _reality;

  public NetworkedMasterClock(IReality reality)
  {
    _reality = reality;
  }

  @Override
  public double setTime(double newTime)
  {
    double rtn = super.setTime(newTime);
    
    /*
     * now signal.. using the real time (unshifted)
     */
    _reality.send(new TimeCommand(_reality.getIdentifier(), rtn - getTimeShift()));
    return rtn;
  }

}

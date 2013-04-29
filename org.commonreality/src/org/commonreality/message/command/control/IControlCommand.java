/*
 * Created on Feb 23, 2007 Copyright (C) 2001-6, Anthony Harrison anh23@pitt.edu
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
package org.commonreality.message.command.control;

import java.io.Serializable;

import org.commonreality.message.command.ICommand;
import org.commonreality.message.request.IRequest;

/**
 * request that sensors and agents change their run state
 * 
 * @author developer
 */
public interface IControlCommand extends ICommand, IRequest, Serializable
{
  /**
   * @author developer
   */
  static public enum State {
    INITIALIZE, START, STOP, SUSPEND, RESUME, RESET, SHUTDOWN, UNKNOWN, CONFIGURE
  };

  /**
   * what state should the participants go to
   * 
   * @return
   */
  public State getState();

  /**
   * return additional data
   * 
   * @return
   */
  public Object getData();
}

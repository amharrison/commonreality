/*
 * Created on May 11, 2007 Copyright (C) 2001-2007, Anthony Harrison
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
package org.commonreality.message.command.object;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.identifier.IIdentifier;
import org.commonreality.message.IMessage;
import org.commonreality.message.impl.BaseMessage;
import org.commonreality.object.delta.IObjectDelta;

/**
 * @author developer
 */
final public class ObjectData extends BaseMessage implements IObjectData,
    Serializable
{
  /**
   * 
   */
  private static final long serialVersionUID = -333010879237813085L;

  /**
   * logger definition
   */
  static private final Log         LOGGER = LogFactory.getLog(ObjectData.class);

  private Collection<IObjectDelta> _data;

  /**
   * @param source
   */
  public ObjectData(IIdentifier source, Collection<IObjectDelta> data)
  {
    super(source);
    _data = new ArrayList<IObjectDelta>(data);

  }

  public IMessage copy()
  {
    return new ObjectData(getSource(), getData());
  }

  /**
   * @see org.commonreality.message.command.object.IObjectData#getObjectData()
   */
  public Collection<IObjectDelta> getData()
  {
    return Collections.unmodifiableCollection(_data);
  }

}

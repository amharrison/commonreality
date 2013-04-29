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
package org.commonreality.message.request.connect;

import java.io.Serializable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.identifier.IIdentifier;
import org.commonreality.message.IMessage;
import org.commonreality.message.impl.BaseAcknowledgementMessage;

/**
 * @author developer
 */
public class ConnectionAcknowledgment extends BaseAcknowledgementMessage
    implements IConnectionAcknowledgement, Serializable
{
  /**
   * 
   */
  private static final long serialVersionUID = -6754010258046439201L;

  /**
   * logger definition
   */
  static private final Log LOGGER = LogFactory
                                      .getLog(ConnectionAcknowledgment.class);

  private String           _responseMessage;

  private IIdentifier      _assignedIdentifier;

  public ConnectionAcknowledgment(IIdentifier source, long requestId,
      String message, IIdentifier assignedId)
  {
    super(source, requestId);
    _responseMessage = message;
    _assignedIdentifier = assignedId;
  }

  public ConnectionAcknowledgment(IIdentifier source, long requestId,
      String message)
  {
    this(source, requestId, message, null);
  }

  public IMessage copy()
  {
    return new ConnectionAcknowledgment(getSource(), getRequestMessageId(),
        getResponseMessage(), getAssignedIdentifier());
  }

  public String getResponseMessage()
  {
    return _responseMessage;
  }

  public IIdentifier getAssignedIdentifier()
  {
    return _assignedIdentifier;
  }
}

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
package org.commonreality.time.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.time.IOwnableClock;

/**
 * @author developer
 */
public class OwnedClock<T> extends BasicClock implements IOwnableClock<T>
{
  /**
   * logger definition
   */
  static private final Log     LOGGER          = LogFactory
                                                   .getLog(OwnedClock.class);

  final private Set<T>         _owners;

  final private Map<T, Double> _requestedTimes;

  final private Set<T>         _heardFrom;

  private boolean              _throwException = true;

  /**
   * 
   */
  public OwnedClock()
  {
    _owners = new HashSet<T>();
    _requestedTimes = new HashMap<T, Double>();
    _heardFrom = new HashSet<T>();
  }

  public void setInvalidAccessThrowsException(boolean throwIt)
  {
    _throwException = throwIt;
  }

  /**
   * @see org.commonreality.time.IOwnableClock#addOwner(java.lang.Object)
   */
  public void addOwner(T owner)
  {
    try
    {
      _lock.lock();
      _owners.add(owner);
    }
    finally
    {
      _lock.unlock();
    }

  }

  /**
   * @see org.commonreality.time.IOwnableClock#getOwners()
   */
  public Collection<T> getOwners()
  {
    try
    {
      _lock.lock();
      return new ArrayList<T>(_owners);
    }
    finally
    {
      _lock.unlock();
    }
  }

  /**
   * @see org.commonreality.time.IOwnableClock#isOwner(java.lang.Object)
   */
  public boolean isOwner(T owner)
  {
    try
    {
      _lock.lock();
      boolean rtn = _owners.contains(owner);
      if (LOGGER.isDebugEnabled())
        LOGGER.debug(owner + " is" + (rtn ? "" : "n't") + " an owner");
      return rtn;
    }
    finally
    {
      _lock.unlock();
    }
  }

  /**
   * @see org.commonreality.time.IOwnableClock#removeOwner(java.lang.Object)
   */
  public void removeOwner(T owner)
  {
    try
    {
      _lock.lock();
      _owners.remove(owner);
      _requestedTimes.remove(owner);
      if (LOGGER.isDebugEnabled()) LOGGER.debug("Removed "+owner);
      if (_heardFrom.containsAll(_owners)) updateTime();
    }
    finally
    {
      _lock.unlock();
    }

  }

  private double mininumRequestedTime()
  {
    double rtn = Double.POSITIVE_INFINITY;
    for (Double request : _requestedTimes.values())
      if (request < rtn) rtn = request;
    return rtn;
  }

  /**
   * @param owner
   * @param requestedTime
   * @return the current time
   */
  public double setTime(T owner, double requestedTime)
  {
    try
    {
      _lock.lock();

      if (!_owners.contains(owner))
      {
        if (_throwException)
          throw new IllegalArgumentException("identifier must be a valid owner");
        else if (LOGGER.isInfoEnabled())
          LOGGER.info(owner + " is not a known owner : " + _owners);
        return getTime();
      }

      if (LOGGER.isDebugEnabled())
        LOGGER.debug(owner + " wants time to be " + requestedTime);

      if (Double.isNaN(requestedTime))
        requestedTime = Double.POSITIVE_INFINITY;
      else
        requestedTime = BasicClock.constrainPrecision(requestedTime);

      _requestedTimes.put(owner, requestedTime);
      _heardFrom.add(owner);

      if (_heardFrom.containsAll(_owners))
        return updateTime();
      else if (LOGGER.isDebugEnabled())
      {
        HashSet<T> remaining = new HashSet<T>(_owners);
        remaining.removeAll(_heardFrom);
        LOGGER.debug("Still waiting to hear from " + remaining);
      }

      return getTime();
    }
    finally
    {
      _lock.unlock();
    }
  }

  /**
   * update the clock..
   */
  protected double updateTime()
  {
    double newTime = 0;
    try
    {
      _lock.lock();
      // _heardFrom.clear();

      double minimum = mininumRequestedTime();

      if (Double.isInfinite(minimum)) minimum = 0.05 + getTime();
      newTime = minimum;

      if (LOGGER.isDebugEnabled()) LOGGER.debug("Updating time " + minimum);

      for(Map.Entry<T, Double> entry : _requestedTimes.entrySet())
        if(entry.getValue()<=minimum)
          _heardFrom.remove(entry.getKey());
    }
    finally
    {
      _lock.unlock();
    }

    return setTime(newTime);
  }

}

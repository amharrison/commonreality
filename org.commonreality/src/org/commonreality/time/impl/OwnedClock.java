package org.commonreality.time.impl;

/*
 * default logging
 */
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.time.IAuthoritativeClock;

/**
 * a clock that can have one or more owners, as determined by an owner object
 * (usually a thread). An authority is always generated. If no owners are set,
 * anyone can update the time.
 * 
 * @author harrison
 */
public class OwnedClock extends BasicClock
{
  /**
   * Logger definition
   */
  static private final transient Log           LOGGER = LogFactory
                                                          .getLog(OwnedClock.class);

  final private BiConsumer<Double, OwnedClock> _changeNotifier;

  public OwnedClock(double minimumTimeIncrement)
  {
    this(minimumTimeIncrement, null);
  }

  public OwnedClock(double minimumTimeIncrement,
      BiConsumer<Double, OwnedClock> universalNotifier)
  {
    super(true, minimumTimeIncrement);
    _changeNotifier = universalNotifier;
  }

  @Override
  protected IAuthoritativeClock createAuthoritativeClock(BasicClock clock)
  {
    return new OwnedAuthoritativeClock(this);
  }

  public static class OwnedAuthoritativeClock extends BasicAuthoritativeClock
  {

    final private Set<Object>         _ownerKeys;

    final private Map<Object, Double> _requestedTimes;

    final private Set<Object>         _ownersAccountedFor;

    public OwnedAuthoritativeClock(BasicClock clock)
    {
      super(clock);
      _ownerKeys = new HashSet<Object>();
      _requestedTimes = new HashMap<Object, Double>();
      _ownersAccountedFor = new HashSet<Object>();
    }

    public void getOwners(final Collection<Object> owners)
    {
      BasicClock.runLocked(getDelegate().getLock(), () -> {
        owners.addAll(_ownerKeys);
      });
    }

    public void addOwner(final Object ownerKey)
    {
      BasicClock.runLocked(getDelegate().getLock(), () -> {
        _ownerKeys.add(ownerKey);
      });
    }

    public void removeOwner(final Object ownerKey)
    {
      BasicClock delegate = getDelegate();
      boolean mustUpdate = BasicClock.runLocked(delegate.getLock(), () -> {
        _ownerKeys.remove(ownerKey);
        _requestedTimes.remove(ownerKey);
        return _ownersAccountedFor.containsAll(_ownerKeys);
      });

      if (mustUpdate) updateTime();
    }

    @Override
    protected boolean requestTimeChange(final double targetTime,
        final Object key)
    {
      BasicClock delegate = getDelegate();
      boolean rtn = BasicClock.runLocked(delegate.getLock(), () -> {
        heardFrom(key, targetTime);
        return _ownersAccountedFor.containsAll(_ownerKeys);
      });
      return rtn;
    }

    @Override
    public CompletableFuture<Double> requestAndWaitForTime(double targetTime,
        final Object key)
    {
      targetTime = BasicClock.constrainPrecision(targetTime);
      BasicClock bc = getDelegate();
      CompletableFuture<Double> rtn = bc.newFuture(targetTime);
      if (requestTimeChange(targetTime, key)) updateTime();
      return rtn;
    }

    @Override
    public CompletableFuture<Double> requestAndWaitForChange(final Object key)
    {
      BasicClock bc = getDelegate();
      CompletableFuture<Double> rtn = bc.newFuture(Double.NaN);
      if (requestTimeChange(Double.NaN, key)) updateTime();
      return rtn;
    }

    private double mininumRequestedTime(final boolean clear)
    {
      return BasicClock
          .runLocked(
              getDelegate().getLock(),
              () -> {
                double rtn = Double.POSITIVE_INFINITY;
                for (Double request : _requestedTimes.values())
                  if (request < rtn) rtn = request;

                if (LOGGER.isDebugEnabled())
                  LOGGER.debug(String.format("Minimum time : %.4f", rtn));

                /*
                 * clear out those that this will apply to. including any
                 * infinities (i.e. any change). Anyone waiting for a time yet
                 * to be reached will still be considered accounted for, so it
                 * will work reguardless of whether or not they make a
                 * subsequent request.
                 */
                if (clear)
                  for (Map.Entry<Object, Double> entry : _requestedTimes
                      .entrySet())
                  {
                    double triggerTime = entry.getValue();
                    /*
                     * those that will wake up are considered unaccounted for.
                     */
                    if (triggerTime <= rtn || !Double.isFinite(triggerTime))
                    {
                      Object owner = entry.getKey();
                      _ownersAccountedFor.remove(owner);
                      // _requestedTimes.remove(owner);
                    }
                  }
                return rtn;
              });
    }

    /**
     * actually find the smallest increment to advance, and do so, firing off
     * completions
     */
    protected void updateTime()
    {
      if (LOGGER.isDebugEnabled())
        LOGGER.debug(String.format("Heard from all owners"));

      double minimumTime = mininumRequestedTime(true);
      if (Double.isInfinite(minimumTime))
        minimumTime = BasicClock.constrainPrecision(getTime()
            + getDelegate().getMinimumTimeIncrement());

      if (LOGGER.isDebugEnabled())
        LOGGER.debug(String.format("Updating time to %.4f", minimumTime));

      OwnedClock delegate = (OwnedClock) getDelegate();
      // actually update and fire things off.
      delegate.setLocalTime(minimumTime);

      if (delegate._changeNotifier != null)
        delegate._changeNotifier.accept(minimumTime, delegate);
    }

    /**
     * in lock
     * 
     * @param key
     * @param requestedTime
     */
    private void heardFrom(Object key, double requestedTime)
    {
      if (_ownerKeys.contains(key) || _ownerKeys.size() == 0)
      {
        if (Double.isNaN(requestedTime))
          requestedTime = Double.POSITIVE_INFINITY;
        else
          requestedTime = BasicClock.constrainPrecision(requestedTime);

        if (LOGGER.isDebugEnabled())
          LOGGER.debug(String.format("Heard from %s, requesting %.4f", key,
              requestedTime));

        _ownersAccountedFor.add(key);
        _requestedTimes.put(key, requestedTime);
      }
      else // not a proper owner.
      if (LOGGER.isErrorEnabled())
        LOGGER
            .error(String
                .format(
                    "Ignoring: %s tried to update clock to %.2f, but is not a known owner (%s)",
                    key, requestedTime, _ownerKeys));
    }
  }
}

package org.commonreality.time;

/*
 * default logging
 */
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.time.impl.OwnedClock;
import org.commonreality.time.impl.OwnedClock.OwnedAuthoritativeClock;
import org.commonreality.time.impl.WrappedClock;
import org.junit.Assert;
import org.junit.Test;

public class WrappedClockTest
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(WrappedClockTest.class);


  /**
   * create three clocks (owned ("a","b"), and two wrapped clocks
   * 
   * @return
   */
  protected IClock[] createNewClocks()
  {
    OwnedClock master = new OwnedClock(0.05);
    ((OwnedAuthoritativeClock) master.getAuthority().get()).addOwner("a");
    ((OwnedAuthoritativeClock) master.getAuthority().get()).addOwner("b");
    WrappedClock one = new WrappedClock(master);
    WrappedClock two = new WrappedClock(master);
    return new IClock[] { master, one, two };
  }


  @Test
  public void testGetTime()
  {
    IClock[] clocks = createNewClocks();

    Assert.assertEquals(0, clocks[0].getTime(), 0.001);
    Assert.assertEquals(0, clocks[1].getTime(), 0.001);
    Assert.assertEquals(0, clocks[2].getTime(), 0.001);

    clocks[1].getAuthority().get().requestAndWaitForTime(1, "a");
    clocks[2].getAuthority().get().requestAndWaitForTime(1.1, "b");

    Assert.assertEquals(1, clocks[1].getTime(), 0.001);

    // should have passed this
    CompletableFuture<Double> reached = clocks[1].waitForTime(0.5);

    Assert.assertTrue(reached.isDone());
  }

  @Test
  public void testGetAuthority()
  {
    IClock[] clocks = createNewClocks();
    Optional<IAuthoritativeClock> auth = clocks[1].getAuthority();

    Assert.assertNotNull(auth);
    Assert.assertTrue(auth.isPresent());
  }

  @Test
  public void testWaitForChange()
  {
    IClock[] clocks = createNewClocks();

    Assert.assertEquals(0, clocks[0].getTime(), 0.001);

    CompletableFuture<Double> reached = clocks[1].waitForChange(); // change
                                                               // relative to
                                                               // current
    // clock hasn't changed yet
    Assert.assertTrue(!reached.isDone());

    // update current
    CompletableFuture<Double> a = clocks[1].getAuthority().get()
        .requestAndWaitForTime(1, "a");
    CompletableFuture<Double> b = clocks[2].getAuthority().get()
        .requestAndWaitForTime(0.5, "b");

    Assert.assertTrue(reached.isDone());
    Assert.assertTrue(b.isDone());
    Assert.assertTrue(!a.isDone());
    Assert.assertEquals(0.5, clocks[0].getTime(), 0.001);
  }

  @Test
  public void testWaitForTime()
  {
    IClock[] clocks = createNewClocks();

    Assert.assertEquals(0, clocks[0].getTime(), 0.001);

    CompletableFuture<Double> reached = clocks[0].waitForTime(0.75); // change
    // relative to
    // current
    // clock hasn't changed yet
    Assert.assertTrue(!reached.isDone());

    // update current
    CompletableFuture<Double> a = clocks[1].getAuthority().get()
        .requestAndWaitForTime(1, "a");
    CompletableFuture<Double> b = clocks[2].getAuthority().get()
        .requestAndWaitForTime(0.5, "b");

    Assert.assertTrue(b.isDone());
    Assert.assertTrue(!a.isDone());

    Assert.assertEquals(0.5, clocks[2].getTime(), 0.001);
    Assert.assertTrue(!reached.isDone());

    clocks[1].getAuthority().get().requestAndWaitForChange("a");
    clocks[2].getAuthority().get().requestAndWaitForTime(1, "b");
    Assert.assertEquals(1, clocks[1].getTime(), 0.001);

    Assert.assertTrue(reached.isDone());
  }

  // @Test
  // public void testTimeShift()
  // {
  // IClock[] clocks = createNewClocks();
  //
  // Assert.assertEquals(0, clocks[0].getTime(), 0.001);
  //
  // clocks[1].getAuthority().get().setLocalTimeShift(1); // "a" is 1 second old
  // // at the start of sim
  // clocks[2].getAuthority().get().setLocalTimeShift(-1); // "b" is -1 second
  // // old at the start of
  // // sim
  //
  // // clock 1 is already at 1, this should be immediate
  // CompletableFuture<Double> a = clocks[1].getAuthority().get()
  // .requestAndWaitForTime(1, "a");
  //
  // // clock 2 is at -1, so this will not have elapsed
  // CompletableFuture<Double> b = clocks[2].getAuthority().get()
  // .requestAndWaitForTime(0.5, "b");
  //
  // Assert.assertTrue(a.isDone());
  // Assert.assertTrue(!b.isDone());
  //
  //
  // Assert.assertEquals(0.5, clocks[2].getTime(), 0.001);
  // Assert.assertTrue(!reached.isDone());
  //
  // clocks[1].getAuthority().get().requestAndWaitForChange("a");
  // clocks[2].getAuthority().get().requestAndWaitForTime(1, "b");
  // Assert.assertEquals(1, clocks[1].getTime(), 0.001);
  //
  // Assert.assertTrue(reached.isDone());
  // }
}

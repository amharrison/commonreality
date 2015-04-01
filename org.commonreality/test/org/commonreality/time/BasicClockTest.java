package org.commonreality.time;

/*
 * default logging
 */
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.time.impl.BasicClock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BasicClockTest
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(BasicClockTest.class);

  
  
  protected IClock createNewClock(boolean withAuth)
  {
    return new BasicClock(withAuth, 0.05);
  }
  
  @Before
  public void setUp() throws Exception
  {
   
  }

  @After
  public void tearDown() throws Exception
  {
  }

  @Test
  public void testGetTime()
  {
    IClock clock = createNewClock(true);
    IAuthoritativeClock ca = clock.getAuthority().get();
    
    Assert.assertEquals(0, clock.getTime(), 0.001);

    ca.requestAndWaitForTime(1, null);// basic clock doesnt need key

    Assert.assertEquals(1, clock.getTime(), 0.001);
    
    CompletableFuture<Double> reached = clock.waitForTime(0.5);
    
    Assert.assertTrue(reached.isDone());
  }

  @Test
  public void testGetAuthority()
  {
    IClock clock = createNewClock(false);
    Optional<IAuthoritativeClock> auth = clock.getAuthority();
    
    Assert.assertNotNull(auth);
    Assert.assertTrue(!auth.isPresent());
    
    clock = createNewClock(true);
    auth = clock.getAuthority();
    
    Assert.assertNotNull(auth);
    Assert.assertTrue(auth.isPresent());
  }

  @Test
  public void testWaitForChange()
  {
    IClock clock = createNewClock(true);
    IAuthoritativeClock ca = clock.getAuthority().get();

    Assert.assertEquals(0, clock.getTime(), 0.001);

    CompletableFuture<Double> reached = clock.waitForChange(); // change
                                                               // relative to
                                                               // current
    // clock hasn't changed yet
    Assert.assertTrue(!reached.isDone());

    // update current
    ca.requestAndWaitForTime(1, null); // basic clock doesnt need key

    Assert.assertEquals(1, clock.getTime(), 0.001);
    Assert.assertTrue(reached.isDone());
  }

  @Test
  public void testWaitForTime()
  {
    IClock clock = createNewClock(true);
    IAuthoritativeClock ca = clock.getAuthority().get();

    Assert.assertEquals(0, clock.getTime(), 0.001);

    CompletableFuture<Double> reached = clock.waitForTime(1); // change
                                                              // relative to
                                                              // current
    // clock hasn't changed yet
    Assert.assertTrue(!reached.isDone());

    // update current
    ca.requestAndWaitForTime(1, null); // basic clock doesnt need key

    Assert.assertEquals(1, clock.getTime(), 0.001);
    Assert.assertTrue(reached.isDone());
  }

}

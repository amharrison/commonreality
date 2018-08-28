package org.commonreality.net.mina;

import org.commonreality.mina.MINANetworkingProvider;
import org.commonreality.net.SimpleNetProviderTest;
import org.junit.Test;

public class MinaNetProviderTest extends SimpleNetProviderTest
{
  
  @Test
  public void testNettyNIO() throws Exception
  {
    testNIOSerializer(MINANetworkingProvider.class.getName());
  }
 
}

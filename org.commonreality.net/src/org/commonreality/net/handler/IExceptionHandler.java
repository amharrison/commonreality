package org.commonreality.net.handler;

/*
 * default logging
 */
import org.commonreality.net.session.ISessionInfo;

@FunctionalInterface
public interface IExceptionHandler
{

  public void exceptionCaught(ISessionInfo<?> session, Throwable thrown);
}

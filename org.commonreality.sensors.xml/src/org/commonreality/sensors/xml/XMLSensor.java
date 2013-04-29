/*
 * Created on May 13, 2007 Copyright (C) 2001-2007, Anthony Harrison
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
package org.commonreality.sensors.xml;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.identifier.IIdentifier;
import org.commonreality.sensors.AbstractSensor;
import org.commonreality.sensors.xml.tasks.NoOp;
import org.commonreality.sensors.xml.tasks.ProcessTimeFrame;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author developer
 */
public class XMLSensor extends AbstractSensor
{

  static public final String DATA_URL    = "XMLSensor.DataURI";

  /**
   * logger definition
   */
  static private final Log   LOGGER      = LogFactory.getLog(XMLSensor.class);

  protected List<Element>    _pendingTimeFrames;

  protected XMLProcessor     _processor;

  protected ExecutorService  _service;

  private NoOp               _noOp;

  private ProcessTimeFrame   _processFrame;

  private volatile boolean   _isStopping = false;

  public XMLSensor()
  {
    super();
    _pendingTimeFrames = new ArrayList<Element>();
    _processor = new XMLProcessor(this);
    _noOp = new NoOp(this);
    _processFrame = new ProcessTimeFrame(_processor);
  }

  /**
   * @see org.commonreality.participant.impl.AbstractParticipant#getName()
   */
  @Override
  public String getName()
  {
    return "XMLSensor";
  }

  public boolean hasPendingTimeFrames()
  {
    synchronized (_pendingTimeFrames)
    {
      return _pendingTimeFrames.size() != 0;
    }
  }

  public void queueNextFrame()
  {
    /*
     * this is actually a perfectly valid condition. If stop was called and the
     * processing thread was awoken from a NoOp, it will call queueNextFrame.
     */
    if (_service == null || _service.isShutdown() || _service.isTerminated()
        || _isStopping)
    {
      if (LOGGER.isDebugEnabled())
        LOGGER.debug("Should terminate, ignoring queue request");
      return;
    }

    Runnable task = _noOp;
    synchronized (_pendingTimeFrames)
    {
      if (hasPendingTimeFrames())
      {
        Element element = _pendingTimeFrames.remove(0);
        if (LOGGER.isDebugEnabled())
          LOGGER.debug("Queueing processing task, remaining frames:"
              + _pendingTimeFrames.size());
        _processFrame.setTimeElement(element);

        task = _processFrame;
      }
      else if (LOGGER.isDebugEnabled()) LOGGER.debug("Queuing noop");
    }

    try
    {
      _service.execute(task);
    }
    catch (RejectedExecutionException rjee)
    {
      /*
       * perfectly natural exception in the case where the xml sensor is
       * shutdown sometime between the entrance to this method and the execution
       * request.
       */
      if (LOGGER.isInfoEnabled())
        LOGGER
            .info("Execution rejected, assuming that sensor is shutting down");
    }
  }

  @Override
  public void start() throws Exception
  {
    _isStopping = false;
    if (LOGGER.isDebugEnabled()) LOGGER.debug("XMLSensor starting");
    queueNextFrame();
    super.start();
  }

  @Override
  public void stop() throws Exception
  {
    if (LOGGER.isDebugEnabled()) LOGGER.debug("Stopping");

    _isStopping = true;
    synchronized (_pendingTimeFrames)
    {
      _pendingTimeFrames.clear();
    }

    // clear out the trash
    FutureTask<Boolean> waitForMe = new FutureTask<Boolean>(new Runnable() {

      public void run()
      {
      }
    }, Boolean.TRUE);
    _service.execute(waitForMe);

    /*
     * interrupt the executor.. so that if it is currently waiting, say for the
     * clock..
     */
    _noOp.interrupt();
    _processFrame.interrupt();

    /*
     * and wait for it to finish
     */
    waitForMe.get();
    super.stop();
  }

  @Override
  public void shutdown() throws Exception
  {
    if (LOGGER.isDebugEnabled()) LOGGER.debug("Shuttingdown");
    try
    {
      if (stateMatches(State.STARTED)) stop();
    }
    finally
    {
      _noOp.interrupt();
      _processFrame.interrupt();
      if (_service != null) _service.shutdownNow();
      _service = null;
      super.shutdown();
    }

  }

  @Override
  public void configure(Map<String, String> options) throws Exception
  {
    if (options.containsKey(DATA_URL)) load(options.get(DATA_URL));
  }

  public void queueFrame(Element element)
  {
    synchronized (_pendingTimeFrames)
    {
      _pendingTimeFrames.add(element);
      if (LOGGER.isDebugEnabled())
        LOGGER.debug("Queued new frame, total:" + _pendingTimeFrames.size());
    }
  }

  /**
   * will process the frame data immediately in the current thread. this should
   * be used with caution as no checks are performed to ensure that the frame
   * doesnt contain time relative information. Nor does it take into account any
   * data that is pending delivery through the normal element frame queue
   * 
   * @param element
   */
  public void executeFrameNow(Element element)
  {
    for (IIdentifier agent : getInterfacedAgents())
      _processor.sendData(agent, _processor.processFrame(agent, element, true));
  }

  @Override
  public void initialize() throws Exception
  {
    super.initialize();
    _service = Executors.newSingleThreadExecutor(getCentralThreadFactory());
  }

  public void load(URL resource)
  {
    try
    {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder parser = factory.newDocumentBuilder();
      Document doc = parser.parse(resource.openStream());

      Element root = doc.getDocumentElement();
      NodeList nl = root.getElementsByTagName("time");
      for (int i = 0; i < nl.getLength(); i++)
      {
        Node node = nl.item(i);
        if (node instanceof Element) queueFrame((Element) node);
      }
    }
    catch (Exception e)
    {
      if (LOGGER.isWarnEnabled())
        LOGGER.warn(String.format("Could not open XMLSensor resource %s (%s)",
            resource, e));
      if (LOGGER.isDebugEnabled()) LOGGER.debug(e);
    }
  }

  protected void load(String dataURI)
  {
    URL url = getClass().getClassLoader().getResource(dataURI);
    try
    {
      if (url == null) url = new URI(dataURI).toURL();

      load(url);
    }
    catch (URISyntaxException e)
    {
      LOGGER.warn("Invalid uri " + dataURI + " ", e);
    }
    catch (MalformedURLException e)
    {
      // TODO Auto-generated catch block
      LOGGER.error("XMLSensor.load threw MalformedURLException : ", e);
    }
  }

}

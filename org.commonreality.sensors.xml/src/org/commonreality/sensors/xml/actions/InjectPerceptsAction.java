package org.commonreality.sensors.xml.actions;

/*
 * default logging
 */
import java.net.URL;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.reality.CommonReality;
import org.commonreality.sensors.ISensor;
import org.commonreality.sensors.xml.XMLSensor;
import org.jactr.core.production.CannotInstantiateException;
import org.jactr.core.production.IInstantiation;
import org.jactr.core.production.VariableBindings;
import org.jactr.core.production.action.DefaultSlotAction;
import org.jactr.core.production.action.IAction;
import org.jactr.core.slot.ISlot;

public class InjectPerceptsAction extends DefaultSlotAction
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(InjectPerceptsAction.class);

  static private final String        RESOURCE_SLOT = "resource";

  private final XMLSensor            _sensor;

  private final URL                  _resource;

  public InjectPerceptsAction()
  {
    _sensor = null;
    _resource = null;
  }

  public InjectPerceptsAction(VariableBindings variableBindings,
      Collection<? extends ISlot> slots, URL resource, XMLSensor sensor)
      throws CannotInstantiateException
  {
    super(variableBindings, slots);
    _sensor = sensor;
    _resource = resource;
  }

  @Override
  public double fire(IInstantiation instantiation, double firingTime)
  {
    /*
     * let's actually do the work, which is trivial
     */
    if (_sensor != null && _resource != null) _sensor.load(_resource);
    return 0;
  }

  public IAction bind(VariableBindings variableBindings)
      throws CannotInstantiateException
  {
    /*
     * make sure we can find the resource
     */
    ISlot resourceSlot = getSlot(RESOURCE_SLOT);
    if (resourceSlot == null)
      throw new CannotInstantiateException(String.format(
          "%s must be specified", RESOURCE_SLOT));

    Object resourceObject = variableBindings.get(RESOURCE_SLOT);
    if (resourceObject == null)
      throw new CannotInstantiateException(String.format(
          "%s must be specified", RESOURCE_SLOT));

    URL resourceURL = getClass().getClassLoader().getResource(
        resourceObject.toString());
    if (resourceURL == null)
      throw new CannotInstantiateException(String.format(
          "Could not find %s on the classpath", resourceObject));

    /*
     * lets also make sure the XMLSensor exists..
     */

    XMLSensor xmlSensor = null;
    for (ISensor sensor : CommonReality.getSensors())
      if (sensor instanceof XMLSensor)
      {
        xmlSensor = (XMLSensor) sensor;
        break;
      }

    if (xmlSensor == null)
      throw new CannotInstantiateException("No XMLSensor installed");

    return new InjectPerceptsAction(variableBindings, getSlots(), resourceURL,
        xmlSensor);
  }

}

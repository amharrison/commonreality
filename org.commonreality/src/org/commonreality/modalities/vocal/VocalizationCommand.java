package org.commonreality.modalities.vocal;

/*
 * default logging
 */
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commonreality.efferent.impl.AbstractEfferentCommand;
import org.commonreality.identifier.IIdentifier;
import org.commonreality.object.IEfferentObject;

public class VocalizationCommand extends AbstractEfferentCommand
{
  /**
   * 
   */
  private static final long serialVersionUID = 7730175468306832689L;
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(VocalizationCommand.class);

  
  public VocalizationCommand(IIdentifier identifier)
  {
    super(identifier);
  }
  
  public VocalizationCommand(IIdentifier identifier, IIdentifier efferentId)
  {
    this(identifier);
    setEfferentIdentifier(efferentId);
  }

  
  public void setText(String text)
  {
    setProperty(VocalConstants.VOCALIZATON, text);
  }
  
  public String getText()
  {
    return (String) getProperty(VocalConstants.VOCALIZATON);
  }

}

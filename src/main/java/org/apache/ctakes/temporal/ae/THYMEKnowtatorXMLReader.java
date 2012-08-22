package org.apache.ctakes.temporal.ae;

import java.io.File;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ctakes.sharp.ae.KnowtatorXMLReader;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.factory.AnalysisEngineFactory;

public class THYMEKnowtatorXMLReader extends KnowtatorXMLReader {

  public static AnalysisEngineDescription getDescription(File knowtatorXMLDirectory)
      throws ResourceInitializationException {
    return AnalysisEngineFactory.createPrimitiveDescription(
        THYMEKnowtatorXMLReader.class,
        KnowtatorXMLReader.PARAM_KNOWTATOR_XML_DIRECTORY,
        knowtatorXMLDirectory);
  }

  @Override
  protected URI getKnowtatorXML(URI uri) {
    File file = new File(uri.getPath());
    String subDir = file.getParentFile().getName();
    Matcher matcher = Pattern.compile("^doc(\\d+)$").matcher(subDir);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Unrecognized subdirectory naming: " + subDir);
    }
    subDir = String.format("Set%02d", Integer.parseInt(matcher.group(1)));
    String fileName = file.getName() + ".knowtator.xml";
    return new File(new File(this.knowtatorXMLDirectory, subDir), fileName).toURI();
  }

  @Override
  protected String[] getAnnotatorNames() {
    return new String[] { "consensus set annotator team", "consensus set_rel annotator team" };
  }

}

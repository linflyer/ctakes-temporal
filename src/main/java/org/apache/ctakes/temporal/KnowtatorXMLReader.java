package org.apache.ctakes.temporal;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ctakes.knowtator.KnowtatorAnnotation;
import org.apache.ctakes.knowtator.KnowtatorXMLParser;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.cleartk.util.ViewURIUtil;
import org.jdom2.JDOMException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

import edu.mayo.bmi.uima.core.type.constants.CONST;
import edu.mayo.bmi.uima.core.type.textsem.EntityMention;
import edu.mayo.bmi.uima.core.type.textsem.EventMention;

public class KnowtatorXMLReader extends JCasAnnotator_ImplBase {

  public static final String PARAM_KNOWTATOR_XML_DIRECTORY = "knowtatorXMLDirectory";

  @ConfigurationParameter(name = PARAM_KNOWTATOR_XML_DIRECTORY, mandatory = true)
  private File knowtatorXMLDirectory;

  @Override
  public void process(JCas jCas) throws AnalysisEngineProcessException {
    // determine Knowtator XML file from URI of CAS
    URI uri = ViewURIUtil.getURI(jCas);
    File file = new File(uri.getPath());
    String subDir = file.getParentFile().getName();
    Matcher matcher = Pattern.compile("^doc(\\d+)$").matcher(subDir);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Unrecognized subdirectory naming: " + subDir);
    }
    subDir = String.format("Set%02d", Integer.parseInt(matcher.group(1)));
    String fileName = file.getName() + ".knowtator.xml";
    File knowtatorFile = new File(new File(this.knowtatorXMLDirectory, subDir), fileName);

    // parse the Knowtator XML file into annotation objects
    KnowtatorXMLParser parser = new KnowtatorXMLParser("consensus set annotator team");
    Collection<KnowtatorAnnotation> annotations;
    try {
      annotations = parser.parse(knowtatorFile);
    } catch (JDOMException e) {
      throw new AnalysisEngineProcessException(e);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }

    // mapping from entity types to their numeric constants
    Map<String, Integer> entityTypes = new HashMap<String, Integer>();
    entityTypes.put("Anatomical_site", CONST.NE_TYPE_ID_ANATOMICAL_SITE);
    entityTypes.put("Disease_Disorder", CONST.NE_TYPE_ID_DISORDER);
    entityTypes.put("Medications/Drugs", CONST.NE_TYPE_ID_DRUG);
    entityTypes.put("???", CONST.NE_TYPE_ID_FINDING);
    entityTypes.put("Procedure", CONST.NE_TYPE_ID_PROCEDURE);
    entityTypes.put("???", CONST.NE_TYPE_ID_UNKNOWN);
    entityTypes.put("Sign_symptom", -1);

    // create a CAS object for each annotation
    for (KnowtatorAnnotation annotation : annotations) {
      if (entityTypes.containsKey(annotation.type)) {
        EntityMention entity = new EntityMention(jCas, annotation.span.begin, annotation.span.end);
        entity.setTypeID(entityTypes.get(annotation.type));
        entity.setConfidence(1.0f);
        entity.setDiscoveryTechnique(CONST.NE_DISCOVERY_TECH_GOLD_ANNOTATION);
        entity.addToIndexes();
      } else if ("EVENT".equals(annotation.type)) {
        EventMention event = new EventMention(jCas, annotation.span.begin, annotation.span.end);
        // TODO: contextualmoduality, contextualaspect, degree, polarity, type, permanence,
        // DocTimeRel, etc.
        event.setConfidence(1.0f);
        event.setDiscoveryTechnique(CONST.NE_DISCOVERY_TECH_GOLD_ANNOTATION);
        event.addToIndexes();
      } else if ("DOCTIME".equals(annotation.type)) {
        // TODO
      } else if ("SECTIONTIME".equals(annotation.type)) {
        // TODO
      } else if ("TIMEX3".equals(annotation.type)) {
        // TODO
      } else if ("ALINK".equals(annotation.type)) {
        // TODO
      } else if ("TLINK".equals(annotation.type)) {
        // TODO
      } else {
        throw new IllegalArgumentException("Unrecognized type: " + annotation.type);
      }
    }
  }
}

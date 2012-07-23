package org.apache.ctakes.temporal;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ctakes.knowtator.KnowtatorAnnotation;
import org.apache.ctakes.knowtator.KnowtatorXMLParser;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.cleartk.util.ViewURIUtil;
import org.jdom2.JDOMException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

import edu.mayo.bmi.uima.core.type.constants.CONST;
import edu.mayo.bmi.uima.core.type.refsem.Event;
import edu.mayo.bmi.uima.core.type.refsem.EventProperties;
import edu.mayo.bmi.uima.core.type.refsem.OntologyConcept;
import edu.mayo.bmi.uima.core.type.refsem.UmlsConcept;
import edu.mayo.bmi.uima.core.type.textsem.EntityMention;
import edu.mayo.bmi.uima.core.type.textsem.EventMention;
import edu.mayo.bmi.uima.core.type.textsem.TimeMention;

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
    entityTypes.put("Procedure", CONST.NE_TYPE_ID_PROCEDURE);
    entityTypes.put("Sign_symptom", CONST.NE_TYPE_ID_FINDING);

    // create a CAS object for each annotation
    for (KnowtatorAnnotation annotation : annotations) {
      // copy the slots so we can remove them as we use them
      Map<String, String> stringSlots = new HashMap<String, String>(annotation.stringSlots);
      Map<String, Boolean> booleanSlots = new HashMap<String, Boolean>(annotation.booleanSlots);
      Map<String, KnowtatorAnnotation> annotationSlots = new HashMap<String, KnowtatorAnnotation>(
          annotation.annotationSlots);

      if (entityTypes.containsKey(annotation.type)) {
        // create the entity mention annotation
        EntityMention entityMention = new EntityMention(
            jCas,
            annotation.span.begin,
            annotation.span.end);
        entityMention.setTypeID(entityTypes.get(annotation.type));
        entityMention.setConfidence(1.0f);
        entityMention.setDiscoveryTechnique(CONST.NE_DISCOVERY_TECH_GOLD_ANNOTATION);

        // convert negation to an integer
        Boolean negation = booleanSlots.remove("Negation");
        entityMention.setPolarity(negation == null ? +1 : negation == true ? -1 : +1);

        // convert status as necessary
        String status = stringSlots.remove("Status");
        if (status != null) {
          if ("HistoryOf".equals(status)) {
            // TODO
          } else if ("FamilyHistoryOf".equals(status)) {
            // TODO
          } else if ("Possible".equals(status)) {
            // TODO
          } else {
            throw new UnsupportedOperationException("Unknown status: " + status);
          }
        }

        // convert code to ontology concept or CUI
        String code = stringSlots.remove("AssociateCode");
        if (code == null) {
          code = stringSlots.remove("associatedCode");
        }
        OntologyConcept ontologyConcept;
        if (entityMention.getTypeID() == CONST.NE_TYPE_ID_DRUG) {
          ontologyConcept = new OntologyConcept(jCas);
          ontologyConcept.setCode(code);
        } else {
          UmlsConcept umlsConcept = new UmlsConcept(jCas);
          umlsConcept.setCui(code);
          ontologyConcept = umlsConcept;
        }
        ontologyConcept.addToIndexes();
        entityMention.setOntologyConceptArr(new FSArray(jCas, 1));
        entityMention.setOntologyConceptArr(0, ontologyConcept);

        // add entity mention to CAS
        entityMention.addToIndexes();

      } else if ("EVENT".equals(annotation.type)) {

        // collect the event properties
        EventProperties eventProperties = new EventProperties(jCas);
        eventProperties.setCategory(stringSlots.remove("type"));
        eventProperties.setContextualModality(stringSlots.remove("contextualmoduality"));
        eventProperties.setContextualAspect(stringSlots.remove("contextualaspect"));
        eventProperties.setDegree(stringSlots.remove("degree"));
        eventProperties.setDocTimeRel(stringSlots.remove("DocTimeRel"));
        eventProperties.setPermanence(stringSlots.remove("permanence"));
        String polarityStr = stringSlots.remove("polarity");
        int polarity;
        if (polarityStr == null || polarityStr.equals("POS")) {
          polarity = +1;
        } else if (polarityStr.equals("NEG")) {
          polarity = -1;
        } else {
          throw new IllegalArgumentException("Invalid polarity: " + polarityStr);
        }
        eventProperties.setPolarity(polarity);

        // create the event object
        Event event = new Event(jCas);
        event.setConfidence(1.0f);
        event.setDiscoveryTechnique(CONST.NE_DISCOVERY_TECH_GOLD_ANNOTATION);

        // create the event mention
        EventMention eventMention = new EventMention(
            jCas,
            annotation.span.begin,
            annotation.span.end);
        eventMention.setConfidence(1.0f);
        eventMention.setDiscoveryTechnique(CONST.NE_DISCOVERY_TECH_GOLD_ANNOTATION);

        // add the links between event, mention and properties
        event.setProperties(eventProperties);
        event.setMentions(new FSArray(jCas, 1));
        event.setMentions(0, eventMention);
        eventMention.setEvent(event);

        // add the annotations to the indexes
        eventProperties.addToIndexes();
        event.addToIndexes();
        eventMention.addToIndexes();

      } else if ("DOCTIME".equals(annotation.type)) {
        // TODO
      } else if ("SECTIONTIME".equals(annotation.type)) {
        // TODO
      } else if ("TIMEX3".equals(annotation.type)) {
        String timexClass = stringSlots.remove("class");
        TimeMention timeMention = new TimeMention(jCas, annotation.span.begin, annotation.span.end);
        timeMention.addToIndexes();
        // TODO
      } else if ("ALINK".equals(annotation.type)) {
        KnowtatorAnnotation source = annotationSlots.remove("Event");
        KnowtatorAnnotation target = annotationSlots.remove("related_to");
        String relationType = stringSlots.remove("Relationtype");
        // TODO
      } else if ("TLINK".equals(annotation.type)) {
        KnowtatorAnnotation source = annotationSlots.remove("Event");
        KnowtatorAnnotation target = annotationSlots.remove("related_to");
        String relationType = stringSlots.remove("Relationtype");
        // TODO
      } else {
        throw new IllegalArgumentException("Unrecognized type: " + annotation.type);
      }

      // make sure all slots have been consumed
      Set<String> remainingSlots = new HashSet<String>();
      remainingSlots.addAll(stringSlots.keySet());
      remainingSlots.addAll(booleanSlots.keySet());
      remainingSlots.addAll(annotationSlots.keySet());
      if (!remainingSlots.isEmpty()) {
        String format = "%s has unprocessed slot(s) %s";
        String message = String.format(format, annotation.type, remainingSlots);
        throw new UnsupportedOperationException(message);
      }
    }
  }
}

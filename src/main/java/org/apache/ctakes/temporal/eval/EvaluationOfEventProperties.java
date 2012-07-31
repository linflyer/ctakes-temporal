package org.apache.ctakes.temporal.eval;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ctakes.temporal.ae.DocTimeRelAnnotator;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.cleartk.classifier.jar.JarClassifierBuilder;
import org.cleartk.classifier.opennlp.MaxentDataWriter;
import org.cleartk.eval.AnnotationStatistics;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.pipeline.JCasIterable;
import org.uimafit.pipeline.SimplePipeline;
import org.uimafit.util.JCasUtil;

import com.google.common.base.Function;
import com.lexicalscope.jewel.cli.CliFactory;

import edu.mayo.bmi.uima.core.type.refsem.Event;
import edu.mayo.bmi.uima.core.type.refsem.EventProperties;
import edu.mayo.bmi.uima.core.type.textsem.EventMention;

public class EvaluationOfEventProperties extends
    Evaluation_ImplBase<Map<String, AnnotationStatistics>> {

  private static final String DOC_TIME_REL = "docTimeRel";

  private static final List<String> PROPERTY_NAMES = Arrays.asList(DOC_TIME_REL);

  public static void main(String[] args) throws Exception {
    Options options = CliFactory.parseArguments(Options.class, args);
    EvaluationOfEventProperties evaluation = new EvaluationOfEventProperties(
        new File("target/eval"),
        options.getRawTextDirectory(),
        options.getKnowtatorXMLDirectory(),
        options.getPatients().getList());
    List<Map<String, AnnotationStatistics>> foldStats = evaluation.crossValidation(4);
    Map<String, AnnotationStatistics> overallStats = new HashMap<String, AnnotationStatistics>();
    for (String name : PROPERTY_NAMES) {
      overallStats.put(name, new AnnotationStatistics());
    }
    for (Map<String, AnnotationStatistics> propertyStats : foldStats) {
      for (String key : propertyStats.keySet()) {
        overallStats.get(key).addAll(propertyStats.get(key));
      }
    }
    for (String name : PROPERTY_NAMES) {
      System.err.println("====================");
      System.err.println(name);
      for (int i = 0; i < foldStats.size(); ++i) {
        System.err.println("--------------------");
        System.err.println("Fold " + i);
        System.err.println(foldStats.get(i).get(name));
      }
      System.err.println("--------------------");
      System.err.println("Overall");
      System.err.println(overallStats.get(name));
    }
  }

  public EvaluationOfEventProperties(
      File baseDirectory,
      File rawTextDirectory,
      File knowtatorXMLDirectory,
      List<Integer> patientSets) {
    super(baseDirectory, rawTextDirectory, knowtatorXMLDirectory, patientSets);
  }

  @Override
  protected List<Class<? extends TOP>> getAnnotationClassesThatShouldBeGoldAtTestTime() {
    List<Class<? extends TOP>> result = super.getAnnotationClassesThatShouldBeGoldAtTestTime();
    result.add(EventMention.class);
    result.add(Event.class);
    result.add(EventProperties.class);
    return result;
  }

  @Override
  protected void train(CollectionReader collectionReader, File directory) throws Exception {
    AggregateBuilder aggregateBuilder = new AggregateBuilder();
    aggregateBuilder.add(this.getPreprocessorTrainDescription());
    aggregateBuilder.add(DocTimeRelAnnotator.createDataWriterDescription(
        MaxentDataWriter.class,
        directory));
    SimplePipeline.runPipeline(collectionReader, aggregateBuilder.createAggregate());
    JarClassifierBuilder.trainAndPackage(directory);
  }

  @Override
  protected Map<String, AnnotationStatistics> test(CollectionReader collectionReader, File directory)
      throws Exception {
    AggregateBuilder aggregateBuilder = new AggregateBuilder();
    aggregateBuilder.add(this.getPreprocessorTestDescription());
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(ClearEventProperties.class));
    aggregateBuilder.add(DocTimeRelAnnotator.createAnnotatorDescription(directory));

    Function<EventMention, ?> eventMentionToSpan = AnnotationStatistics.annotationToSpan();
    Map<String, Function<EventMention, String>> propertyGetters;
    propertyGetters = new HashMap<String, Function<EventMention, String>>();
    for (String name : PROPERTY_NAMES) {
      propertyGetters.put(name, getPropertyGetter(name));
    }

    Map<String, AnnotationStatistics> statsMap = new HashMap<String, AnnotationStatistics>();
    statsMap.put(DOC_TIME_REL, new AnnotationStatistics());
    for (JCas jCas : new JCasIterable(collectionReader, aggregateBuilder.createAggregate())) {
      JCas goldView = jCas.getView(GOLD_VIEW_NAME);
      JCas systemView = jCas.getView(CAS.NAME_DEFAULT_SOFA);
      Collection<EventMention> goldEvents = JCasUtil.select(goldView, EventMention.class);
      Collection<EventMention> systemEvents = JCasUtil.select(systemView, EventMention.class);
      for (String name : PROPERTY_NAMES) {
        statsMap.get(name).add(
            goldEvents,
            systemEvents,
            eventMentionToSpan,
            propertyGetters.get(name));
      }
    }
    return statsMap;
  }

  private static Function<EventMention, String> getPropertyGetter(final String propertyName) {
    return new Function<EventMention, String>() {
      @Override
      public String apply(EventMention eventMention) {
        Feature feature = eventMention.getType().getFeatureByBaseName(propertyName);
        return eventMention.getEvent().getProperties().getFeatureValueAsString(feature);
      }
    };
  }

  public static class ClearEventProperties extends JCasAnnotator_ImplBase {
    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
      for (EventProperties eventProperties : JCasUtil.select(jCas, EventProperties.class)) {
        eventProperties.setAspect(null);
        eventProperties.setCategory(null);
        eventProperties.setContextualAspect(null);
        eventProperties.setContextualModality(null);
        eventProperties.setDegree(null);
        eventProperties.setDocTimeRel(null);
        eventProperties.setPermanence(null);
        eventProperties.setPolarity(0);
      }
    }

  }
}

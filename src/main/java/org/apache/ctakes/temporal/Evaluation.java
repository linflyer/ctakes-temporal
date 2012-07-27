package org.apache.ctakes.temporal;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ctakes.temporal.Evaluation.Statistics;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.util.CasCopier;
import org.cleartk.classifier.CleartkAnnotator;
import org.cleartk.classifier.jar.DefaultDataWriterFactory;
import org.cleartk.classifier.jar.JarClassifierBuilder;
import org.cleartk.classifier.jar.JarClassifierFactory;
import org.cleartk.classifier.opennlp.MaxentDataWriter;
import org.cleartk.eval.AnnotationStatistics;
import org.cleartk.eval.Evaluation_ImplBase;
import org.cleartk.syntax.opennlp.PosTaggerAnnotator;
import org.cleartk.syntax.opennlp.SentenceAnnotator;
import org.cleartk.timeml.time.TimeAnnotator;
import org.cleartk.timeml.type.Time;
import org.cleartk.token.stem.snowball.DefaultSnowballStemmer;
import org.cleartk.token.tokenizer.TokenAnnotator;
import org.cleartk.util.ae.UriToDocumentTextAnnotator;
import org.cleartk.util.cr.UriCollectionReader;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.component.NoOpAnnotator;
import org.uimafit.component.ViewCreatorAnnotator;
import org.uimafit.component.ViewTextCopierAnnotator;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.factory.ExternalResourceFactory;
import org.uimafit.factory.TypeSystemDescriptionFactory;
import org.uimafit.pipeline.JCasIterable;
import org.uimafit.pipeline.SimplePipeline;
import org.uimafit.util.JCasUtil;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import edu.mayo.bmi.uima.core.ae.SentenceDetector;
import edu.mayo.bmi.uima.core.ae.SimpleSegmentAnnotator;
import edu.mayo.bmi.uima.core.ae.TokenizerAnnotatorPTB;
import edu.mayo.bmi.uima.core.resource.SuffixMaxentModelResourceImpl;
import edu.mayo.bmi.uima.core.type.textsem.EntityMention;
import edu.mayo.bmi.uima.core.type.textsem.EventMention;
import edu.mayo.bmi.uima.core.type.textsem.TimeMention;

public class Evaluation extends Evaluation_ImplBase<Integer, Statistics> {

  static class Statistics {
    public AnnotationStatistics events = new AnnotationStatistics();

    public AnnotationStatistics times = new AnnotationStatistics();
  }

  private static final String GOLD_VIEW_NAME = "GoldView";

  public static void main(String[] args) throws Exception {
    // parse command line arguments
    File rawTextDirectory = new File(args[0]);
    File knowtatorXMLDirectory = new File(args[1]);
    String setsDescription = args[2];
    List<Integer> patientSets = new ArrayList<Integer>();
    for (String part : setsDescription.split("\\s*,\\s*")) {
      Matcher matcher = Pattern.compile("(\\d+)-(\\d+)").matcher(part);
      if (matcher.matches()) {
        int begin = Integer.parseInt(matcher.group(1));
        int end = Integer.parseInt(matcher.group(2));
        for (int i = begin; i <= end; ++i) {
          patientSets.add(i);
        }
      } else {
        patientSets.add(Integer.parseInt(part));
      }
    }

    Evaluation evaluation = new Evaluation(
        new File("target/eval"),
        rawTextDirectory,
        knowtatorXMLDirectory);
    List<Statistics> foldStats = evaluation.crossValidation(patientSets, 3);
    for (Statistics stats : foldStats) {
      System.err.println("EVENTS");
      System.err.println(stats.events);
      System.err.println("TIMES");
      System.err.println(stats.times);
    }
    List<AnnotationStatistics> eventStats = new ArrayList<AnnotationStatistics>();
    List<AnnotationStatistics> timeStats = new ArrayList<AnnotationStatistics>();
    for (Statistics stats : foldStats) {
      eventStats.add(stats.events);
      timeStats.add(stats.times);
    }
    System.err.println("OVERALL");
    System.err.println("EVENTS");
    System.err.println(AnnotationStatistics.addAll(eventStats));
    System.err.println("Gold:");
    println(evaluation.goldEvents);
    System.err.println("System:");
    println(evaluation.systemEvents);
    System.err.println("TIMES");
    System.err.println(AnnotationStatistics.addAll(timeStats));
    System.err.println("Gold:");
    println(evaluation.goldTimes);
    System.err.println("System:");
    println(evaluation.systemTimes);
  }

  private static void println(Multiset<String> multiset) {
    List<String> keys = new ArrayList<String>(multiset.elementSet());
    Collections.sort(keys);
    for (String key : keys) {
      System.err.printf("%4d %s\n", multiset.count(key), key);
    }
  }

  private File rawTextDirectory;

  private File knowtatorXMLDirectory;

  public Multiset<String> goldEvents, systemEvents, goldTimes, systemTimes;

  public Evaluation(File baseDirectory, File rawTextDirectory, File knowtatorXMLDirectory) {
    super(baseDirectory);
    this.rawTextDirectory = rawTextDirectory;
    this.knowtatorXMLDirectory = knowtatorXMLDirectory;
    this.goldEvents = HashMultiset.create();
    this.systemEvents = HashMultiset.create();
    this.goldTimes = HashMultiset.create();
    this.systemTimes = HashMultiset.create();
  }

  @Override
  protected CollectionReader getCollectionReader(List<Integer> patientSets) throws Exception {
    List<File> files = new ArrayList<File>();
    for (Integer set : patientSets) {
      File setTextDirectory = new File(this.rawTextDirectory, "doc" + set);
      for (File file : setTextDirectory.listFiles()) {
        files.add(file);
      }
    }
    return CollectionReaderFactory.createCollectionReader(
        UriCollectionReader.class,
        UriCollectionReader.PARAM_FILES,
        files);
  }

  @Override
  protected void train(CollectionReader collectionReader, File directory) throws Exception {
    AggregateBuilder aggregateBuilder = new AggregateBuilder();
    aggregateBuilder.add(UriToDocumentTextAnnotator.getDescription());
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
        KnowtatorXMLReader.class,
        KnowtatorXMLReader.PARAM_KNOWTATOR_XML_DIRECTORY,
        this.knowtatorXMLDirectory));
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(SimpleSegmentAnnotator.class));
    AnalysisEngineDescription sentenceDetector = AnalysisEngineFactory.createPrimitiveDescription(SentenceDetector.class);
    ExternalResourceFactory.createDependencyAndBind(
        sentenceDetector,
        "MaxentModel",
        SuffixMaxentModelResourceImpl.class,
        SentenceDetector.class.getResource("/sentdetect/sdmed.mod").toURI().toString());
    aggregateBuilder.add(sentenceDetector);
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(TokenizerAnnotatorPTB.class));
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
        EventAnnotator.class,
        CleartkAnnotator.PARAM_IS_TRAINING,
        true,
        DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
        MaxentDataWriter.class,
        DefaultDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
        directory));
    SimplePipeline.runPipeline(collectionReader, aggregateBuilder.createAggregate());
    JarClassifierBuilder.trainAndPackage(directory, "2000", "5");
  }

  @Override
  protected Statistics test(CollectionReader collectionReader, File directory) throws Exception {
    AggregateBuilder aggregateBuilder = new AggregateBuilder();
    aggregateBuilder.add(UriToDocumentTextAnnotator.getDescription());
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
        ViewCreatorAnnotator.class,
        ViewCreatorAnnotator.PARAM_VIEW_NAME,
        GOLD_VIEW_NAME));
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
        ViewTextCopierAnnotator.class,
        ViewTextCopierAnnotator.PARAM_SOURCE_VIEW_NAME,
        CAS.NAME_DEFAULT_SOFA,
        ViewTextCopierAnnotator.PARAM_DESTINATION_VIEW_NAME,
        GOLD_VIEW_NAME));
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
        KnowtatorXMLReader.class,
        KnowtatorXMLReader.PARAM_KNOWTATOR_XML_DIRECTORY,
        this.knowtatorXMLDirectory), CAS.NAME_DEFAULT_SOFA, GOLD_VIEW_NAME);
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(GoldEntityMentionCopier.class));
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(SimpleSegmentAnnotator.class));
    AnalysisEngineDescription sentenceDetector = AnalysisEngineFactory.createPrimitiveDescription(SentenceDetector.class);
    ExternalResourceFactory.createDependencyAndBind(
        sentenceDetector,
        "MaxentModel",
        SuffixMaxentModelResourceImpl.class,
        SentenceDetector.class.getResource("/sentdetect/sdmed.mod").toURI().toString());
    aggregateBuilder.add(sentenceDetector);
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(TokenizerAnnotatorPTB.class));
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
        EventAnnotator.class,
        CleartkAnnotator.PARAM_IS_TRAINING,
        false,
        JarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
        new File(directory, "model.jar")));
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
        NoOpAnnotator.class,
        TypeSystemDescriptionFactory.createTypeSystemDescription("org.cleartk.TypeSystem")));
    aggregateBuilder.add(SentenceAnnotator.getDescription());
    aggregateBuilder.add(TokenAnnotator.getDescription());
    aggregateBuilder.add(PosTaggerAnnotator.getDescription());
    aggregateBuilder.add(DefaultSnowballStemmer.getDescription("English"));
    aggregateBuilder.add(TimeAnnotator.FACTORY.getAnnotatorDescription());

    Statistics stats = new Statistics();
    for (JCas jCas : new JCasIterable(collectionReader, aggregateBuilder.createAggregate())) {
      JCas goldView = jCas.getView(GOLD_VIEW_NAME);
      JCas systemView = jCas.getView(CAS.NAME_DEFAULT_SOFA);
      Collection<EventMention> goldEvents = JCasUtil.select(goldView, EventMention.class);
      Collection<EventMention> systemEvents = JCasUtil.select(systemView, EventMention.class);
      stats.events.add(goldEvents, systemEvents);
      this.goldEvents.addAll(JCasUtil.toText(goldEvents));
      this.systemEvents.addAll(JCasUtil.toText(systemEvents));
      Collection<TimeMention> goldTimes = JCasUtil.select(goldView, TimeMention.class);
      Collection<Time> systemTimes = JCasUtil.select(systemView, Time.class);
      stats.times.add(goldTimes, systemTimes);
      this.goldTimes.addAll(JCasUtil.toText(goldTimes));
      this.systemTimes.addAll(JCasUtil.toText(systemTimes));
    }
    return stats;
  }

  public static class GoldEntityMentionCopier extends JCasAnnotator_ImplBase {

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
      JCas goldView, systemView;
      try {
        goldView = jCas.getView(GOLD_VIEW_NAME);
        systemView = jCas.getView(CAS.NAME_DEFAULT_SOFA);
      } catch (CASException e) {
        throw new AnalysisEngineProcessException(e);
      }
      CasCopier copier = new CasCopier(goldView.getCas(), systemView.getCas());
      for (EntityMention goldMention : JCasUtil.select(goldView, EntityMention.class)) {
        Annotation copy = (Annotation) copier.copyFs(goldMention);
        Feature sofaFeature = copy.getType().getFeatureByBaseName("sofa");
        copy.setFeatureValue(sofaFeature, systemView.getSofa());
        copy.addToIndexes();
      }

    }

  }
}

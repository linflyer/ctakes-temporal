package org.apache.ctakes.temporal.eval;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.eval.AnnotationStatistics;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.pipeline.JCasIterable;
import org.uimafit.pipeline.SimplePipeline;
import org.uimafit.util.JCasUtil;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

public abstract class EvaluationOfAnnotationSpans_ImplBase extends
    Evaluation_ImplBase<AnnotationStatistics> {

  private Multiset<String> goldTexts, systemTexts;

  public EvaluationOfAnnotationSpans_ImplBase(
      File baseDirectory,
      File rawTextDirectory,
      File knowtatorXMLDirectory,
      List<Integer> patientSets) {
    super(baseDirectory, rawTextDirectory, knowtatorXMLDirectory, patientSets);
    this.goldTexts = HashMultiset.create();
    this.systemTexts = HashMultiset.create();
  }

  protected abstract AnalysisEngineDescription getDataWriterDescription(File directory)
      throws ResourceInitializationException;

  protected abstract void trainAndPackage(File directory) throws Exception;

  @Override
  protected void train(CollectionReader collectionReader, File directory) throws Exception {
    AggregateBuilder aggregateBuilder = new AggregateBuilder();
    aggregateBuilder.add(this.getPreprocessorTrainDescription());
    aggregateBuilder.add(this.getDataWriterDescription(directory));
    SimplePipeline.runPipeline(collectionReader, aggregateBuilder.createAggregate());
    this.trainAndPackage(directory);
  }

  protected abstract AnalysisEngineDescription getAnnotatorDescription(File directory)
      throws ResourceInitializationException;

  protected abstract Collection<? extends Annotation> getGoldAnnotations(JCas jCas);

  protected abstract Collection<? extends Annotation> getSystemAnnotations(JCas jCas);

  @Override
  protected AnnotationStatistics test(CollectionReader collectionReader, File directory)
      throws Exception {
    AggregateBuilder aggregateBuilder = new AggregateBuilder();
    aggregateBuilder.add(this.getPreprocessorTestDescription());
    aggregateBuilder.add(this.getAnnotatorDescription(directory));

    AnnotationStatistics stats = new AnnotationStatistics();
    for (JCas jCas : new JCasIterable(collectionReader, aggregateBuilder.createAggregate())) {
      JCas goldView = jCas.getView(GOLD_VIEW_NAME);
      JCas systemView = jCas.getView(CAS.NAME_DEFAULT_SOFA);
      Collection<? extends Annotation> goldAnnotations = this.getGoldAnnotations(goldView);
      Collection<? extends Annotation> systemAnnotations = this.getSystemAnnotations(systemView);
      stats.add(goldAnnotations, systemAnnotations);
      Set<String> goldTexts = new HashSet<String>(JCasUtil.toText(goldAnnotations));
      Set<String> systemTexts = new HashSet<String>(JCasUtil.toText(systemAnnotations));
      Set<String> goldOnly = new HashSet<String>();
      goldOnly.addAll(goldTexts);
      goldOnly.removeAll(systemTexts);
      Set<String> systemOnly = new HashSet<String>();
      systemOnly.addAll(systemTexts);
      systemOnly.removeAll(goldTexts);
      this.goldTexts.addAll(goldOnly);
      this.systemTexts.addAll(systemOnly);
    }
    return stats;
  }

  public void writeGoldAnnotationTexts(File file) throws FileNotFoundException {
    this.printAnnotationTexts(this.goldTexts, file);
  }

  public void writeSystemAnnotationTexts(File file) throws FileNotFoundException {
    this.printAnnotationTexts(this.systemTexts, file);
  }

  private void printAnnotationTexts(Multiset<String> multiset, File file)
      throws FileNotFoundException {
    PrintWriter writer = new PrintWriter(file);
    try {
      List<String> keys = new ArrayList<String>(multiset.elementSet());
      Collections.sort(keys);
      for (String key : keys) {
        writer.printf("%2d %s\n", multiset.count(key), key);
      }
    } finally {
      writer.close();
    }
  }
}

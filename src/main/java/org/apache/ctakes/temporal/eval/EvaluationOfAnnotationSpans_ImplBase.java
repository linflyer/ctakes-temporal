package org.apache.ctakes.temporal.eval;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

public abstract class EvaluationOfAnnotationSpans_ImplBase extends Evaluation_ImplBase {

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
      this.goldTexts.addAll(JCasUtil.toText(goldAnnotations));
      this.systemTexts.addAll(JCasUtil.toText(systemAnnotations));
    }
    return stats;
  }

  public void printGoldAnnotationTexts() {
    this.printAnnotationTexts(this.goldTexts);
  }

  public void printSystemAnnotationTexts() {
    this.printAnnotationTexts(this.systemTexts);
  }

  private void printAnnotationTexts(Multiset<String> multiset) {
    List<String> keys = new ArrayList<String>(multiset.elementSet());
    Collections.sort(keys);
    for (String key : keys) {
      System.err.printf("%4d %s\n", multiset.count(key), key);
    }
  }
}

package org.apache.ctakes.temporal.ae;

import java.io.File;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.classifier.CleartkAnnotator;
import org.cleartk.classifier.DataWriter;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.Instance;
import org.cleartk.classifier.feature.extractor.ContextExtractor;
import org.cleartk.classifier.feature.extractor.ContextExtractor.Covered;
import org.cleartk.classifier.feature.extractor.ContextExtractor.Following;
import org.cleartk.classifier.feature.extractor.ContextExtractor.Preceding;
import org.cleartk.classifier.feature.extractor.simple.CombinedExtractor;
import org.cleartk.classifier.feature.extractor.simple.CoveredTextExtractor;
import org.cleartk.classifier.feature.extractor.simple.TypePathExtractor;
import org.cleartk.classifier.jar.DefaultDataWriterFactory;
import org.cleartk.classifier.jar.JarClassifierFactory;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.util.JCasUtil;

import edu.mayo.bmi.uima.core.type.syntax.BaseToken;
import edu.mayo.bmi.uima.core.type.textsem.EventMention;

public class DocTimeRelAnnotator extends CleartkAnnotator<String> {

  public static AnalysisEngineDescription createDataWriterDescription(
      Class<? extends DataWriter<String>> dataWriterClass,
      File outputDirectory) throws ResourceInitializationException {
    return AnalysisEngineFactory.createPrimitiveDescription(
        DocTimeRelAnnotator.class,
        CleartkAnnotator.PARAM_IS_TRAINING,
        true,
        DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
        dataWriterClass,
        DefaultDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
        outputDirectory);
  }

  public static AnalysisEngineDescription createAnnotatorDescription(File modelDirectory)
      throws ResourceInitializationException {
    return AnalysisEngineFactory.createPrimitiveDescription(
        DocTimeRelAnnotator.class,
        CleartkAnnotator.PARAM_IS_TRAINING,
        false,
        JarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
        new File(modelDirectory, "model.jar"));
  }

  private ContextExtractor<?> contextExtractor;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    CombinedExtractor baseExtractor = new CombinedExtractor(
        new CoveredTextExtractor(),
        new TypePathExtractor(BaseToken.class, "partOfSpeech"));
    this.contextExtractor = new ContextExtractor<BaseToken>(
        BaseToken.class,
        baseExtractor,
        new Preceding(3),
        new Covered(),
        new Following(3));
  }

  @Override
  public void process(JCas jCas) throws AnalysisEngineProcessException {
    for (EventMention eventMention : JCasUtil.select(jCas, EventMention.class)) {
      List<Feature> features = this.contextExtractor.extract(jCas, eventMention);
      if (this.isTraining()) {
        String outcome = eventMention.getEvent().getProperties().getDocTimeRel();
        this.dataWriter.write(new Instance<String>(outcome, features));
      } else {
        String outcome = this.classifier.classify(features);
        eventMention.getEvent().getProperties().setDocTimeRel(outcome);
      }
    }
  }
}

package org.apache.ctakes.temporal.ae;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.cleartk.classifier.chunking.BIOChunking;
import org.cleartk.classifier.feature.extractor.CleartkExtractor;
import org.cleartk.classifier.feature.extractor.CleartkExtractor.Following;
import org.cleartk.classifier.feature.extractor.CleartkExtractor.Preceding;
import org.cleartk.classifier.feature.extractor.simple.CharacterCategoryPatternExtractor;
import org.cleartk.classifier.feature.extractor.simple.CharacterCategoryPatternExtractor.PatternType;
import org.cleartk.classifier.feature.extractor.simple.CombinedExtractor;
import org.cleartk.classifier.feature.extractor.simple.CoveredTextExtractor;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;
import org.cleartk.classifier.feature.extractor.simple.TypePathExtractor;
import org.cleartk.classifier.jar.DefaultDataWriterFactory;
import org.cleartk.classifier.jar.JarClassifierFactory;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.util.JCasUtil;

import edu.mayo.bmi.uima.core.type.syntax.BaseToken;
import edu.mayo.bmi.uima.core.type.textsem.EntityMention;
import edu.mayo.bmi.uima.core.type.textsem.EventMention;
import edu.mayo.bmi.uima.core.type.textspan.Sentence;

public class EventAnnotator extends CleartkAnnotator<String> {

  public static AnalysisEngineDescription createDataWriterDescription(
      Class<? extends DataWriter<String>> dataWriterClass,
      File outputDirectory) throws ResourceInitializationException {
    return AnalysisEngineFactory.createPrimitiveDescription(
        EventAnnotator.class,
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
        EventAnnotator.class,
        CleartkAnnotator.PARAM_IS_TRAINING,
        false,
        JarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
        new File(modelDirectory, "model.jar"));
  }

  protected List<SimpleFeatureExtractor> tokenFeatureExtractors;

  protected List<CleartkExtractor> contextFeatureExtractors;

  private BIOChunking<BaseToken, EntityMention> entityChunking;

  private BIOChunking<BaseToken, EventMention> eventChunking;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);

    // define chunkings
    this.entityChunking = new BIOChunking<BaseToken, EntityMention>(
        BaseToken.class,
        EntityMention.class);
    this.eventChunking = new BIOChunking<BaseToken, EventMention>(
        BaseToken.class,
        EventMention.class);

    // add features: word, stem, pos
    this.tokenFeatureExtractors = new ArrayList<SimpleFeatureExtractor>();
    this.tokenFeatureExtractors.addAll(Arrays.asList(
        new CoveredTextExtractor(),
        new CharacterCategoryPatternExtractor(PatternType.ONE_PER_CHAR),
        new TypePathExtractor(BaseToken.class, "partOfSpeech")));

    // add window of features before and after
    CombinedExtractor subExtractor = new CombinedExtractor(
        new CoveredTextExtractor(),
        new TypePathExtractor(BaseToken.class, "partOfSpeech"));
    this.contextFeatureExtractors = new ArrayList<CleartkExtractor>();
    this.contextFeatureExtractors.add(new CleartkExtractor(
        BaseToken.class,
        subExtractor,
        new Preceding(3),
        new Following(3)));
  }

  @Override
  public void process(JCas jCas) throws AnalysisEngineProcessException {
    // classify tokens within each sentence
    for (Sentence sentence : JCasUtil.select(jCas, Sentence.class)) {
      List<BaseToken> tokens = JCasUtil.selectCovered(jCas, BaseToken.class, sentence);

      // during training, the list of all outcomes for the tokens
      List<String> outcomes;
      if (this.isTraining()) {
        List<EventMention> events = JCasUtil.selectCovered(jCas, EventMention.class, sentence);
        outcomes = this.eventChunking.createOutcomes(jCas, tokens, events);
      }
      // during prediction, the list of outcomes predicted so far
      else {
        outcomes = new ArrayList<String>();
      }

      // extract features for all tokens
      List<EntityMention> entities = JCasUtil.selectCovered(jCas, EntityMention.class, sentence);
      List<String> tokenEntityTags = this.entityChunking.createOutcomes(jCas, tokens, entities);
      int tokenIndex = -1;
      int window = 2;
      for (BaseToken token : tokens) {
        ++tokenIndex;

        List<Feature> features = new ArrayList<Feature>();
        // features from token attributes
        for (SimpleFeatureExtractor extractor : this.tokenFeatureExtractors) {
          features.addAll(extractor.extract(jCas, token));
        }
        // features from surrounding tokens
        for (CleartkExtractor extractor : this.contextFeatureExtractors) {
          features.addAll(extractor.extractWithin(jCas, token, sentence));
        }
        // features from surrounding entities
        int begin = Math.max(tokenIndex - window, 0);
        int end = Math.min(tokenIndex + window, tokenEntityTags.size());
        for (int i = begin; i < end; ++i) {
          features.add(new Feature("EntityTag_" + (i - begin), tokenEntityTags.get(i)));
        }
        // features from previous classifications
        int nPreviousClassifications = 2;
        for (int i = nPreviousClassifications; i > 0; --i) {
          int index = tokenIndex - i;
          String previousOutcome = index < 0 ? "O" : outcomes.get(index);
          features.add(new Feature("PreviousOutcome_" + i, previousOutcome));
        }
        // if training, write to data file
        if (this.isTraining()) {
          String outcome = outcomes.get(tokenIndex);
          this.dataWriter.write(new Instance<String>(outcome, features));
        }

        // if predicting, add prediction to outcomes
        else {
          outcomes.add(this.classifier.classify(features));
        }
      }

      // during prediction, convert chunk labels to events and add them to the CAS
      if (!this.isTraining()) {
        this.eventChunking.createChunks(jCas, tokens, outcomes);
      }
    }
  }
}

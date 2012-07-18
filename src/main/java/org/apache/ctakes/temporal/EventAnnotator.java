package org.apache.ctakes.temporal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.classifier.CleartkAnnotator;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.Instance;
import org.cleartk.classifier.Instances;
import org.cleartk.classifier.chunking.BIOChunking;
import org.cleartk.classifier.feature.extractor.ContextExtractor;
import org.cleartk.classifier.feature.extractor.ContextExtractor.Following;
import org.cleartk.classifier.feature.extractor.ContextExtractor.Preceding;
import org.cleartk.classifier.feature.extractor.simple.CoveredTextExtractor;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;
import org.cleartk.classifier.feature.extractor.simple.TypePathExtractor;
import org.uimafit.util.JCasUtil;

import edu.mayo.bmi.uima.core.type.syntax.BaseToken;
import edu.mayo.bmi.uima.core.type.textsem.EntityMention;
import edu.mayo.bmi.uima.core.type.textsem.EventMention;
import edu.mayo.bmi.uima.core.type.textspan.Sentence;

public class EventAnnotator extends CleartkAnnotator<String> {

  protected List<SimpleFeatureExtractor> tokenFeatureExtractors;

  protected List<ContextExtractor<?>> contextFeatureExtractors;

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
        new TypePathExtractor(BaseToken.class, "lemmaEntries/key"),
        new TypePathExtractor(BaseToken.class, "partOfSpeech")));

    // add window of features before and after
    this.contextFeatureExtractors = new ArrayList<ContextExtractor<?>>();
    this.contextFeatureExtractors.add(new ContextExtractor<BaseToken>(
        BaseToken.class,
        new CoveredTextExtractor(),
        new Preceding(3),
        new Following(3)));
  }

  @Override
  public void process(JCas jCas) throws AnalysisEngineProcessException {
    // classify tokens within each sentence
    for (Sentence sentence : JCasUtil.select(jCas, Sentence.class)) {
      List<BaseToken> tokens = JCasUtil.selectCovered(jCas, BaseToken.class, sentence);

      // extract features for all tokens
      List<List<Feature>> featureLists = new ArrayList<List<Feature>>();
      List<EntityMention> entities = JCasUtil.selectCovered(jCas, EntityMention.class, sentence);
      List<String> tokenEntityTags = this.entityChunking.createOutcomes(jCas, tokens, entities);
      int tokenIndex = 0;
      int window = 2;
      for (BaseToken token : tokens) {
        List<Feature> features = new ArrayList<Feature>();
        for (SimpleFeatureExtractor extractor : this.tokenFeatureExtractors) {
          features.addAll(extractor.extract(jCas, token));
        }
        for (ContextExtractor<?> extractor : this.contextFeatureExtractors) {
          features.addAll(extractor.extractWithin(jCas, token, sentence));
        }
        int begin = Math.max(tokenIndex - window, 0);
        int end = Math.min(tokenIndex + window, tokenEntityTags.size());
        for (int i = begin; i < end; ++i) {
          features.add(new Feature("EntityTag_" + i, tokenEntityTags.get(i)));
        }
        featureLists.add(features);
        ++tokenIndex;
      }

      // during training, convert events to chunk labels and write the training Instances
      if (this.isTraining()) {
        List<EventMention> events = JCasUtil.selectCovered(jCas, EventMention.class, sentence);
        List<String> outcomes = this.eventChunking.createOutcomes(jCas, tokens, events);
        for (Instance<String> instance : Instances.toInstances(outcomes, featureLists)) {
          this.dataWriter.write(instance);
        }
      }

      // during prediction, convert chunk labels to events and add them to the CAS
      else {
        List<String> outcomes = new ArrayList<String>();
        for (List<Feature> featureList : featureLists) {
          outcomes.add(this.classifier.classify(featureList));
        }
        this.eventChunking.createChunks(jCas, tokens, outcomes);
      }
    }
  }
}

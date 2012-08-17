package org.apache.ctakes.temporal.eval;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.eval.AnnotationStatistics;
import org.cleartk.syntax.opennlp.PosTaggerAnnotator;
import org.cleartk.syntax.opennlp.SentenceAnnotator;
import org.cleartk.timeml.time.TimeAnnotator;
import org.cleartk.timeml.type.Time;
import org.cleartk.token.stem.snowball.DefaultSnowballStemmer;
import org.cleartk.token.tokenizer.TokenAnnotator;
import org.uimafit.component.NoOpAnnotator;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.TypeSystemDescriptionFactory;
import org.uimafit.util.JCasUtil;

import com.lexicalscope.jewel.cli.CliFactory;

import edu.mayo.bmi.uima.core.type.textsem.TimeMention;

public class EvaluationOfTimeSpans extends EvaluationOfAnnotationSpans_ImplBase {

  public static void main(String[] args) throws Exception {
    Options options = CliFactory.parseArguments(Options.class, args);
    EvaluationOfTimeSpans evaluation = new EvaluationOfTimeSpans(
        new File("target/eval"),
        options.getRawTextDirectory(),
        options.getKnowtatorXMLDirectory(),
        options.getPatients().getList());
    evaluation.setLogging(Level.FINE, new File("target/eval/time-errors.log"));
    List<AnnotationStatistics<String>> foldStats = evaluation.crossValidation(4);
    for (AnnotationStatistics<String> stats : foldStats) {
      System.err.println(stats);
    }
    System.err.println("OVERALL");
    System.err.println(AnnotationStatistics.addAll(foldStats));
  }

  public EvaluationOfTimeSpans(
      File baseDirectory,
      File rawTextDirectory,
      File knowtatorXMLDirectory,
      List<Integer> patientSets) {
    super(
        baseDirectory,
        rawTextDirectory,
        knowtatorXMLDirectory,
        patientSets,
        EnumSet.of(AnnotatorType.PART_OF_SPEECH_TAGS));
  }

  @Override
  protected AnalysisEngineDescription getDataWriterDescription(File directory)
      throws ResourceInitializationException {
    // for the moment, not training a model - just using the ClearTK one
    return AnalysisEngineFactory.createPrimitiveDescription(NoOpAnnotator.class);
  }

  @Override
  protected void trainAndPackage(File directory) throws Exception {
    // for the moment, not training a model - just using the ClearTK one
  }

  @Override
  protected AnalysisEngineDescription getAnnotatorDescription(File directory)
      throws ResourceInitializationException {
    // for the moment, not training a model - just using the ClearTK one
    AggregateBuilder aggregateBuilder = new AggregateBuilder();
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
        NoOpAnnotator.class,
        TypeSystemDescriptionFactory.createTypeSystemDescription("org.cleartk.TypeSystem")));
    aggregateBuilder.add(SentenceAnnotator.getDescription());
    aggregateBuilder.add(TokenAnnotator.getDescription());
    aggregateBuilder.add(PosTaggerAnnotator.getDescription());
    aggregateBuilder.add(DefaultSnowballStemmer.getDescription("English"));
    aggregateBuilder.add(TimeAnnotator.FACTORY.getAnnotatorDescription());
    return aggregateBuilder.createAggregateDescription();
  }

  @Override
  protected Collection<? extends Annotation> getGoldAnnotations(JCas jCas) {
    return JCasUtil.select(jCas, TimeMention.class);
  }

  @Override
  protected Collection<? extends Annotation> getSystemAnnotations(JCas jCas) {
    return JCasUtil.select(jCas, Time.class);
  }
}

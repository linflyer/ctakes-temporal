package org.apache.ctakes.temporal.eval;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;

import org.apache.ctakes.temporal.ae.EventAnnotator;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.classifier.jar.JarClassifierBuilder;
import org.cleartk.classifier.libsvm.LIBSVMStringOutcomeDataWriter;
import org.cleartk.eval.AnnotationStatistics;
import org.uimafit.util.JCasUtil;

import com.lexicalscope.jewel.cli.CliFactory;

import edu.mayo.bmi.uima.core.type.textsem.EntityMention;
import edu.mayo.bmi.uima.core.type.textsem.EventMention;

public class EvaluationOfEventSpans extends EvaluationOfAnnotationSpans_ImplBase {

  public static void main(String[] args) throws Exception {
    Options options = CliFactory.parseArguments(Options.class, args);
    EvaluationOfEventSpans evaluation = new EvaluationOfEventSpans(
        new File("target/eval"),
        options.getRawTextDirectory(),
        options.getKnowtatorXMLDirectory(),
        options.getPatients().getList());
    evaluation.setLogging(Level.FINE, new File("target/eval/event-errors.log"));
    List<AnnotationStatistics<String>> foldStats = evaluation.crossValidation(4);
    for (AnnotationStatistics<String> stats : foldStats) {
      System.err.println(stats);
    }
    System.err.println("OVERALL");
    System.err.println(AnnotationStatistics.addAll(foldStats));
  }

  public EvaluationOfEventSpans(
      File baseDirectory,
      File rawTextDirectory,
      File knowtatorXMLDirectory,
      List<Integer> patientSets) {
    super(baseDirectory, rawTextDirectory, knowtatorXMLDirectory, patientSets, EnumSet.of(
        AnnotatorType.PART_OF_SPEECH_TAGS,
        AnnotatorType.UMLS_NAMED_ENTITIES));
  }

  @Override
  protected AnalysisEngineDescription getDataWriterDescription(File directory)
      throws ResourceInitializationException {
    return EventAnnotator.createDataWriterDescription(
        LIBSVMStringOutcomeDataWriter.class,
        directory);
  }

  @Override
  protected void trainAndPackage(File directory) throws Exception {
    JarClassifierBuilder.trainAndPackage(directory, "-c", "10000");
  }

  @Override
  protected List<Class<? extends TOP>> getAnnotationClassesThatShouldBeGoldAtTestTime() {
    List<Class<? extends TOP>> result = super.getAnnotationClassesThatShouldBeGoldAtTestTime();
    result.add(EntityMention.class);
    return result;
  }

  @Override
  protected AnalysisEngineDescription getAnnotatorDescription(File directory)
      throws ResourceInitializationException {
    return EventAnnotator.createAnnotatorDescription(directory);
  }

  @Override
  protected Collection<? extends Annotation> getGoldAnnotations(JCas jCas) {
    return JCasUtil.select(jCas, EventMention.class);
  }

  @Override
  protected Collection<? extends Annotation> getSystemAnnotations(JCas jCas) {
    return JCasUtil.select(jCas, EventMention.class);
  }
}

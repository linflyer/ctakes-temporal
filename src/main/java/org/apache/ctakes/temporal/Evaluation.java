package org.apache.ctakes.temporal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
import org.cleartk.classifier.CleartkAnnotator;
import org.cleartk.classifier.jar.DefaultDataWriterFactory;
import org.cleartk.classifier.opennlp.MaxentDataWriter;
import org.cleartk.eval.AnnotationStatistics;
import org.cleartk.eval.Evaluation_ImplBase;
import org.cleartk.util.ViewURIUtil;
import org.uimafit.component.JCasCollectionReader_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.pipeline.SimplePipeline;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class Evaluation extends Evaluation_ImplBase<Integer, AnnotationStatistics> {

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
    evaluation.crossValidation(patientSets, 3);
  }

  private File rawTextDirectory;

  private File knowtatorXMLDirectory;

  public Evaluation(File baseDirectory, File rawTextDirectory, File knowtatorXMLDirectory) {
    super(baseDirectory);
    this.rawTextDirectory = rawTextDirectory;
    this.knowtatorXMLDirectory = knowtatorXMLDirectory;
  }

  @Override
  protected CollectionReader getCollectionReader(List<Integer> patientSets) throws Exception {
    List<String> paths = new ArrayList<String>();
    for (Integer set : patientSets) {
      File setTextDirectory = new File(this.rawTextDirectory, "doc" + set);
      for (File file : setTextDirectory.listFiles()) {
        paths.add(file.getPath());
      }
    }
    return CollectionReaderFactory.createCollectionReader(
        FilesCollectionReader.class,
        FilesCollectionReader.PARAM_FILES,
        paths);
  }

  @Override
  protected void train(CollectionReader collectionReader, File directory) throws Exception {
    AggregateBuilder aggregateBuilder = new AggregateBuilder();
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
        KnowtatorXMLReader.class,
        KnowtatorXMLReader.PARAM_KNOWTATOR_XML_DIRECTORY,
        this.knowtatorXMLDirectory));
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
        EventAnnotator.class,
        CleartkAnnotator.PARAM_IS_TRAINING,
        true,
        DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
        MaxentDataWriter.class,
        DefaultDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
        directory));
    SimplePipeline.runPipeline(collectionReader, aggregateBuilder.createAggregate());
  }

  @Override
  protected AnnotationStatistics test(CollectionReader collectionReader, File directory)
      throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  public static class FilesCollectionReader extends JCasCollectionReader_ImplBase {

    public static final String PARAM_FILES = "files";

    @ConfigurationParameter(name = PARAM_FILES, mandatory = true)
    private List<File> files;

    private int fileIndex;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
      super.initialize(context);
      this.fileIndex = 0;
    }

    @Override
    public boolean hasNext() throws IOException, CollectionException {
      return this.fileIndex < this.files.size();
    }

    @Override
    public Progress[] getProgress() {
      return new Progress[] { new ProgressImpl(this.fileIndex, this.files.size(), Progress.ENTITIES) };
    }

    @Override
    public void getNext(JCas jCas) throws IOException, CollectionException {
      File file = this.files.get(this.fileIndex);
      String text = Files.toString(file, Charsets.US_ASCII);
      jCas.setDocumentText(text);
      ViewURIUtil.setURI(jCas, file.toURI());
      ++this.fileIndex;
    }

  }

}

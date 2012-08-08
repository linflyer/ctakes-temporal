package org.apache.ctakes.temporal.eval;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.ctakes.sharp.ae.KnowtatorXMLReader;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.cas.TOP;
import org.cleartk.util.ae.UriToDocumentTextAnnotator;
import org.cleartk.util.cr.UriCollectionReader;
import org.uimafit.component.ViewCreatorAnnotator;
import org.uimafit.component.ViewTextCopierAnnotator;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.ExternalResourceFactory;

import com.lexicalscope.jewel.cli.Option;

import edu.mayo.bmi.uima.core.ae.SentenceDetector;
import edu.mayo.bmi.uima.core.ae.SimpleSegmentAnnotator;
import edu.mayo.bmi.uima.core.ae.TokenizerAnnotatorPTB;
import edu.mayo.bmi.uima.core.resource.FileResourceImpl;
import edu.mayo.bmi.uima.core.resource.JdbcConnectionResourceImpl;
import edu.mayo.bmi.uima.core.resource.LuceneIndexReaderResourceImpl;
import edu.mayo.bmi.uima.core.resource.SuffixMaxentModelResourceImpl;
import edu.mayo.bmi.uima.lookup.ae.UmlsDictionaryLookupAnnotator;
import edu.mayo.bmi.uima.pos_tagger.POSTagger;

public abstract class Evaluation_ImplBase<STATISTICS_TYPE> extends
    org.cleartk.eval.Evaluation_ImplBase<Integer, STATISTICS_TYPE> {

  protected final String GOLD_VIEW_NAME = "GoldView";

  static interface Options {

    @Option(longName = "text")
    public File getRawTextDirectory();

    @Option(longName = "xml")
    public File getKnowtatorXMLDirectory();

    @Option(longName = "patients")
    public CommandLine.IntegerRanges getPatients();
  }

  protected File rawTextDirectory;

  protected File knowtatorXMLDirectory;

  protected List<Integer> patientSets;

  public Evaluation_ImplBase(
      File baseDirectory,
      File rawTextDirectory,
      File knowtatorXMLDirectory,
      List<Integer> patientSets) {
    super(baseDirectory);
    this.rawTextDirectory = rawTextDirectory;
    this.knowtatorXMLDirectory = knowtatorXMLDirectory;
    this.patientSets = patientSets;
  }

  public List<STATISTICS_TYPE> crossValidation(int nFolds) throws Exception {
    return this.crossValidation(this.patientSets, nFolds);
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
    return UriCollectionReader.getCollectionReaderFromFiles(files);
  }

  protected AnalysisEngineDescription getPreprocessorTrainDescription() throws Exception {
    return this.getPreprocessorDescription(PipelineType.TRAIN);
  }

  protected AnalysisEngineDescription getPreprocessorTestDescription() throws Exception {
    return this.getPreprocessorDescription(PipelineType.TEST);
  }

  protected List<Class<? extends TOP>> getAnnotationClassesThatShouldBeGoldAtTestTime() {
    return new ArrayList<Class<? extends TOP>>();
  }

  private static enum PipelineType {
    TRAIN, TEST
  };

  private AnalysisEngineDescription getPreprocessorDescription(PipelineType pipelineType)
      throws Exception {
    AggregateBuilder aggregateBuilder = new AggregateBuilder();
    aggregateBuilder.add(UriToDocumentTextAnnotator.getDescription());
    switch (pipelineType) {
      case TRAIN:
        aggregateBuilder.add(KnowtatorXMLReader.getDescription(this.knowtatorXMLDirectory));
        break;
      case TEST:
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
        aggregateBuilder.add(
            KnowtatorXMLReader.getDescription(this.knowtatorXMLDirectory),
            CAS.NAME_DEFAULT_SOFA,
            GOLD_VIEW_NAME);
        for (Class<? extends TOP> annotationClass : this.getAnnotationClassesThatShouldBeGoldAtTestTime()) {
          aggregateBuilder.add(AnnotationCopier.getDescription(
              GOLD_VIEW_NAME,
              CAS.NAME_DEFAULT_SOFA,
              annotationClass));
        }
        break;
    }
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
        POSTagger.class,
        POSTagger.POS_MODEL_FILE_PARAM,
        "models/mayo-pos.zip",
        POSTagger.TAG_DICTIONARY_PARAM,
        "models/tag.dictionary.txt",
        POSTagger.CASE_SENSITIVE_PARAM,
        true));
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
        UmlsDictionaryLookupAnnotator.class,
        "UMLSAddr",
        "https://uts-ws.nlm.nih.gov/restful/isValidUMLSUser",
        "UMLSVendor",
        "NLM-6515182895",
        "UMLSUser",
        System.getProperty("umls.user"),
        "UMLSPW",
        System.getProperty("umls.password"),
        "LookupDescriptor",
        ExternalResourceFactory.createExternalResourceDescription(
            FileResourceImpl.class,
            getUMLSFile("/lookup/LookupDesc_Db.xml")),
        "DbConnection",
        ExternalResourceFactory.createExternalResourceDescription(
            JdbcConnectionResourceImpl.class,
            "",
            "DriverClassName",
            "org.hsqldb.jdbcDriver",
            "URL",
            "jdbc:hsqldb:file:" + getUMLSFile("/lookup/umls2011ab") + "/umls"),
        "RxnormIndexReader",
        ExternalResourceFactory.createExternalResourceDescription(
            LuceneIndexReaderResourceImpl.class,
            "",
            "UseMemoryIndex",
            true,
            "IndexDirectory",
            getUMLSFile("/lookup/rxnorm_index")),
        "OrangeBookIndexReader",
        ExternalResourceFactory.createExternalResourceDescription(
            LuceneIndexReaderResourceImpl.class,
            "",
            "UseMemoryIndex",
            true,
            "IndexDirectory",
            getUMLSFile("/lookup/OrangeBook"))));
    return aggregateBuilder.createAggregateDescription();
  }

  private static File getUMLSFile(String path) throws URISyntaxException {
    return new File(UmlsDictionaryLookupAnnotator.class.getResource(path).toURI());
  }
}

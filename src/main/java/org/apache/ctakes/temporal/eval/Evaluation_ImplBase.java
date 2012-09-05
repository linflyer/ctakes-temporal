package org.apache.ctakes.temporal.eval;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.ctakes.temporal.ae.THYMEKnowtatorXMLReader;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.cleartk.util.ae.UriToDocumentTextAnnotator;
import org.cleartk.util.cr.UriCollectionReader;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.component.ViewCreatorAnnotator;
import org.uimafit.component.ViewTextCopierAnnotator;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.ExternalResourceFactory;
import org.uimafit.util.JCasUtil;

import com.google.common.collect.Lists;
import com.lexicalscope.jewel.cli.Option;

import edu.mayo.bmi.nlp.parser.ae.ClearParserDependencyParserAE;
import edu.mayo.bmi.nlp.parser.ae.ClearParserSemanticRoleLabelerAE;
import edu.mayo.bmi.uima.adjuster.ChunkAdjuster;
import edu.mayo.bmi.uima.cdt.ae.ContextDependentTokenizerAnnotator;
import edu.mayo.bmi.uima.chunker.Chunker;
import edu.mayo.bmi.uima.chunker.DefaultChunkCreator;
import edu.mayo.bmi.uima.core.ae.OverlapAnnotator;
import edu.mayo.bmi.uima.core.ae.SentenceDetector;
import edu.mayo.bmi.uima.core.ae.SimpleSegmentAnnotator;
import edu.mayo.bmi.uima.core.ae.TokenizerAnnotatorPTB;
import edu.mayo.bmi.uima.core.resource.FileResourceImpl;
import edu.mayo.bmi.uima.core.resource.JdbcConnectionResourceImpl;
import edu.mayo.bmi.uima.core.resource.LuceneIndexReaderResourceImpl;
import edu.mayo.bmi.uima.core.resource.SuffixMaxentModelResourceImpl;
import edu.mayo.bmi.uima.core.type.syntax.Chunk;
import edu.mayo.bmi.uima.core.type.textsem.EntityMention;
import edu.mayo.bmi.uima.core.type.textspan.LookupWindowAnnotation;
import edu.mayo.bmi.uima.lookup.ae.UmlsDictionaryLookupAnnotator;
import edu.mayo.bmi.uima.lvg.ae.LvgAnnotator;
import edu.mayo.bmi.uima.lvg.resource.LvgCmdApiResourceImpl;
import edu.mayo.bmi.uima.pos_tagger.POSTagger;

public abstract class Evaluation_ImplBase<STATISTICS_TYPE> extends
    org.cleartk.eval.Evaluation_ImplBase<Integer, STATISTICS_TYPE> {

  public enum AnnotatorType {
    PART_OF_SPEECH_TAGS, UMLS_NAMED_ENTITIES, LEXICAL_VARIANTS, DEPENDENCY_PARSERS, SEMANTIC_ROLES
  };

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

  private Set<AnnotatorType> annotatorFlags;

  public Evaluation_ImplBase(
      File baseDirectory,
      File rawTextDirectory,
      File knowtatorXMLDirectory,
      List<Integer> patientSets,
      Set<AnnotatorType> annotatorFlags) {
    super(baseDirectory);
    this.rawTextDirectory = rawTextDirectory;
    this.knowtatorXMLDirectory = knowtatorXMLDirectory;
    this.patientSets = patientSets;
    this.annotatorFlags = annotatorFlags;
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
        aggregateBuilder.add(THYMEKnowtatorXMLReader.getDescription(this.knowtatorXMLDirectory));
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
            THYMEKnowtatorXMLReader.getDescription(this.knowtatorXMLDirectory),
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
    // identify segments
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(SimpleSegmentAnnotator.class));
    // identify sentences
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
        SentenceDetector.class,
        "MaxentModel",
        ExternalResourceFactory.createExternalResourceDescription(
            SuffixMaxentModelResourceImpl.class,
            SentenceDetector.class.getResource("/sentdetect/sdmed.mod"))));
    // identify tokens
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(TokenizerAnnotatorPTB.class));
    // merge some tokens
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(ContextDependentTokenizerAnnotator.class));

    // identify part-of-speech tags if requested
    if (this.annotatorFlags.contains(AnnotatorType.PART_OF_SPEECH_TAGS)) {
      aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
          POSTagger.class,
          POSTagger.POS_MODEL_FILE_PARAM,
          "models/mayo-pos.zip",
          POSTagger.TAG_DICTIONARY_PARAM,
          "models/tag.dictionary.txt",
          POSTagger.CASE_SENSITIVE_PARAM,
          true));
    }

    // identify UMLS named entities if requested
    if (this.annotatorFlags.contains(AnnotatorType.UMLS_NAMED_ENTITIES)) {
      // remove gold mentions if they're there (we'll add cTAKES mentions later instead)
      aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(EntityMentionRemover.class));
      // identify chunks
      aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
          Chunker.class,
          "ChunkerModelFile",
          new File(Chunker.class.getResource("/models/chunk-model.claims-1.5.zip").toURI()),
          "ChunkCreatorClass",
          DefaultChunkCreator.class));
      // adjust NP in NP NP to span both
      aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
          ChunkAdjuster.class,
          "ChunkPattern",
          new String[] { "NP", "NP" },
          "IndexOfTokenToInclude",
          1));
      // adjust NP in NP PP NP to span all three
      aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
          ChunkAdjuster.class,
          "ChunkPattern",
          new String[] { "NP", "PP", "NP" },
          "IndexOfTokenToInclude",
          2));
      // add lookup windows for each NP
      aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(CopyNPChunksToLookupWindowAnnotations.class));
      // maximize lookup windows
      aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
          OverlapAnnotator.class,
          "A_ObjectClass",
          LookupWindowAnnotation.class,
          "B_ObjectClass",
          LookupWindowAnnotation.class,
          "OverlapType",
          "A_ENV_B",
          "ActionType",
          "DELETE",
          "DeleteAction",
          new String[] { "selector=B" }));
      // add UMLS on top of lookup windows
      aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
          UmlsDictionaryLookupAnnotator.class,
          "UMLSAddr",
          "https://uts-ws.nlm.nih.gov/restful/isValidUMLSUser",
          "UMLSVendor",
          "NLM-6515182895",
          "UMLSUser",
          "linflyer",//System.getProperty("umls.user"),
          "UMLSPW",
          "cMm!1940",//System.getProperty("umls.password"),
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
    }
    
    //add lvg annotator
    if (this.annotatorFlags.contains(AnnotatorType.LEXICAL_VARIANTS)) {
    	String[] XeroxTreebankMap = {"adj|JJ","adv|RB","aux|AUX","compl|CS","conj|CC","det|DET","modal|MD","noun|NN","prep|IN","pron|PRP","verb|VB"};
    	String[] ExclusionSet = {"and","And","by","By","for","For","in","In","of","Of","on","On","the","The","to","To","with","With"};
    	AnalysisEngineDescription lvgAnnotator = AnalysisEngineFactory.createPrimitiveDescription(
    		LvgAnnotator.class, 
    	    //"LvgCmdApi","edu.mayo.bmi.uima.lvg.resource.LvgCmdApiResource",
    		"UseSegments", false,
    		"SegmentsToSkip",new String[0],
    		"UseCmdCache", false,
    		"CmdCacheFileLocation", LvgAnnotator.class.getResource("/lvg/2005_norm.voc").toURI().toString(),
    		"CmdCacheFrequencyCutoff", 20,
    		"ExclusionSet", ExclusionSet,
    		"XeroxTreebankMap", XeroxTreebankMap,
    		"LemmaCacheFileLocation", LvgAnnotator.class.getResource("/lvg/2005_lemma.voc").toURI().toString(),
    		"UseLemmaCache", false,
    		"LemmaCacheFrequencyCutoff", 20,
    		"PostLemmas",true);
    	ExternalResourceFactory.createDependencyAndBind(
    		lvgAnnotator,
            "LvgCmdApi",
            LvgCmdApiResourceImpl.class,
            LvgAnnotator.class.getResource("/lvg/data/config/lvg.properties").toURI().toString());
    	aggregateBuilder.add(lvgAnnotator);
    }
    
    //add dependency parser
    if (this.annotatorFlags.contains(AnnotatorType.DEPENDENCY_PARSERS)) {
    	AnalysisEngineDescription dependencyParserAnnotator = AnalysisEngineFactory.createPrimitiveDescription(
    		ClearParserDependencyParserAE.class,
    		"ParserModelFileName",null,
    		"ParserAlgorithmName", "shift-pop",
    		"UseLemmatizer",true);
    	aggregateBuilder.add(dependencyParserAnnotator);
    }
    
    //add semantic role labeler
    if (this.annotatorFlags.contains(AnnotatorType.SEMANTIC_ROLES)) {
    	AnalysisEngineDescription slrAnnotator = AnalysisEngineFactory.createPrimitiveDescription(
    		ClearParserSemanticRoleLabelerAE.class,
    		"ParserModelFileName",null,
    		"UseLemmatizer",true);
    	aggregateBuilder.add(slrAnnotator);
    }
    return aggregateBuilder.createAggregateDescription();
  }

  private static File getUMLSFile(String path) throws URISyntaxException {
    return new File(UmlsDictionaryLookupAnnotator.class.getResource(path).toURI());
  }

  public static class CopyNPChunksToLookupWindowAnnotations extends JCasAnnotator_ImplBase {

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
      for (Chunk chunk : JCasUtil.select(jCas, Chunk.class)) {
        if (chunk.getChunkType().equals("NP")) {
          new LookupWindowAnnotation(jCas, chunk.getBegin(), chunk.getEnd()).addToIndexes();
        }
      }
    }
  }

  public static class EntityMentionRemover extends JCasAnnotator_ImplBase {

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
      for (EntityMention mention : Lists.newArrayList(JCasUtil.select(jCas, EntityMention.class))) {
        mention.removeFromIndexes();
      }
    }
  }
}

package org.apache.ctakes.temporal.eval;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.ctakes.sharp.ae.KnowtatorXMLReader;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.util.ViewURIUtil;
import org.cleartk.util.ae.UriToDocumentTextAnnotator;
import org.cleartk.util.cr.UriCollectionReader;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.pipeline.JCasIterable;
import org.uimafit.util.JCasUtil;

import com.google.common.base.Function;
import com.google.common.collect.Ordering;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.Option;

import edu.mayo.bmi.uima.core.type.relation.BinaryTextRelation;

public class PrintRelations {

  interface Options {

    @Option(longName = "text")
    public File getRawTextDirectory();

    @Option(longName = "xml")
    public File getKnowtatorXMLDirectory();

    @Option(longName = "patients")
    public CommandLine.IntegerRanges getPatients();
  }

  public static void main(String[] args) throws Exception {

    // parse command line options
    Options options = CliFactory.parseArguments(Options.class, args);
    File rawTextDirectory = options.getRawTextDirectory();
    File knowtatorXMLDirectory = options.getKnowtatorXMLDirectory();
    List<Integer> patientSets = options.getPatients().getList();

    // collect the files for all the patients
    List<File> files = new ArrayList<File>();
    for (Integer set : patientSets) {
      File subDir = new File(rawTextDirectory, "doc" + set);
      files.addAll(Arrays.asList(subDir.listFiles()));
    }

    // construct reader and Knowtator XML parser
    CollectionReader reader = UriCollectionReader.getCollectionReaderFromFiles(files);
    AggregateBuilder aggregateBuilder = new AggregateBuilder();
    aggregateBuilder.add(UriToDocumentTextAnnotator.getDescription());
    aggregateBuilder.add(KnowtatorXMLReader.getDescription(knowtatorXMLDirectory));

    // walk through each document in the collection
    for (JCas jCas : new JCasIterable(reader, aggregateBuilder.createAggregate())) {
      System.err.println(ViewURIUtil.getURI(jCas));

      // collect all relations and sort them by the order they appear in the text
      Collection<BinaryTextRelation> relations = JCasUtil.select(jCas, BinaryTextRelation.class);
      List<BinaryTextRelation> relationList = new ArrayList<BinaryTextRelation>(relations);
      Collections.sort(relationList, BY_RELATION_OFFSETS);

      // print out the relations for visual inspection
      for (BinaryTextRelation relation : relationList) {
        Annotation source = relation.getArg1().getArgument();
        Annotation target = relation.getArg2().getArgument();
        String type = relation.getCategory();
        System.err.printf("%s(%s,%s)\n", type, source.getCoveredText(), target.getCoveredText());
      }
      System.err.println();
    }
  }

  /**
   * Orders relations to match their order in the text (as defined by the spans of their arguments)
   */
  private static final Ordering<BinaryTextRelation> BY_RELATION_OFFSETS = Ordering.<Integer> natural().lexicographical().onResultOf(
      new Function<BinaryTextRelation, Set<Integer>>() {
        @Override
        public Set<Integer> apply(BinaryTextRelation relation) {
          Annotation arg1 = relation.getArg1().getArgument();
          Annotation arg2 = relation.getArg2().getArgument();
          return new TreeSet<Integer>(Arrays.asList(arg1.getBegin(), arg2.getBegin()));
        }
      });
}

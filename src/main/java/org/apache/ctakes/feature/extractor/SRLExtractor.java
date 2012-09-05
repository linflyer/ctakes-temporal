package org.apache.ctakes.feature.extractor;

import java.util.Collections;
import java.util.List;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.feature.extractor.CleartkExtractorException;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;

import org.uimafit.util.JCasUtil;


import edu.mayo.bmi.uima.core.type.syntax.BaseToken;
import edu.mayo.bmi.uima.core.type.textsem.Predicate;
import edu.mayo.bmi.uima.core.type.textsem.SemanticArgument;
import edu.mayo.bmi.uima.core.type.textsem.SemanticRoleRelation;


public class SRLExtractor implements SimpleFeatureExtractor {

	@Override
	public List<Feature> extract(JCas jCas, Annotation focusAnnotation)
			throws CleartkExtractorException {
		// TODO: don't iterate over the entire CAS for each focusAnnotation; use JCasUtil.indexCovering and cache the results so that we only do this once per CAS


		Feature feature = new Feature("NoRole");
		for (Predicate predicate : JCasUtil.select(jCas, Predicate.class)) {
			
			for (BaseToken token : JCasUtil.selectCovered(jCas, BaseToken.class, predicate)) {
				if (token.equals(focusAnnotation)){//token.getBegin()==focusAnnotation.getBegin()){
					feature = new Feature("Predicate");
					//System.out.println("*******************\tPredicate is :"+ predicate.getCoveredText());
					return Collections.singletonList(feature);
				}
			}
			
			for (SemanticRoleRelation relation : JCasUtil.select(predicate.getRelations(), SemanticRoleRelation.class)) {
				SemanticArgument arg = relation.getArgument();
				//System.out.format("\tArg: %s=%s \n", arg.getLabel(), arg.getCoveredText());
				for (BaseToken token : JCasUtil.selectCovered(jCas, BaseToken.class, arg)) {
					if (token.equals(focusAnnotation)){//token.getBegin()==focusAnnotation.getBegin()){
						String label = arg.getLabel();
						feature = new Feature(label);
						//System.out.println("*******************\tfeature is :");
						return Collections.singletonList(feature);
					}
				}
			}
		}
		
		return Collections.singletonList(feature);
	}

}

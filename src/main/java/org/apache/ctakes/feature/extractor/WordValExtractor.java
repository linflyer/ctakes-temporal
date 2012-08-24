package org.apache.ctakes.feature.extractor;

import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.feature.extractor.CleartkExtractorException;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;



public class WordValExtractor implements SimpleFeatureExtractor {
	
	private Hashtable<String, Float> word_val;
	private Float default_val;

	public WordValExtractor(Hashtable<String, Float> wordVal) {
		super();
		this.word_val = wordVal;
		this.default_val = calDefault();
	}

	private Float calDefault() {
		float sum = 0;
		if (!this.word_val.isEmpty()){
			for(Float val: this.word_val.values()){
				sum+= val.floatValue();
			}
			sum = sum/this.word_val.size();
		}
		return new Float(sum);
	}

	@Override
	public List<Feature> extract(JCas view, Annotation token)
			throws CleartkExtractorException {
		
		Feature feature = new Feature(this.default_val);
		if (!word_val.isEmpty()){
			Float num = word_val.get(token.getCoveredText());
			if(num != null){
				feature = new Feature(num);
				System.out.println("*********** word_val: "+ token.getCoveredText()+" :: "+ num.toString());
			}
		}
		
		return Collections.singletonList(feature);
	}

}

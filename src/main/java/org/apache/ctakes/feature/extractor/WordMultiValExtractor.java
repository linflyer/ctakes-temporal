package org.apache.ctakes.feature.extractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.feature.extractor.CleartkExtractorException;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;



public class WordMultiValExtractor implements SimpleFeatureExtractor {
	
	private HashMap<String, Float[]> word_val;
	private Float[] default_val;

	public WordMultiValExtractor(HashMap<String, Float[]> wordVal) {
		super();
		this.word_val = wordVal;
		this.default_val = calDefault();
	}

	private Float[] calDefault() {
		Float[] sum = null;
		if (!this.word_val.isEmpty()){
			int dim = this.word_val.values().iterator().next().length;
			sum = new Float[dim];
			for (int i=0;i<dim;i++){
				sum[i] = (float)0;
			}
			for(Float[] val: this.word_val.values()){
				for (int i=0;i<dim;i++){
					sum[i] += val[i].floatValue(); 
				}
			}
			for (int i=0;i<dim;i++){
				sum[i] = sum[i]/this.word_val.size();
			}
		}
		return sum;
	}

	@Override
	public List<Feature> extract(JCas view, Annotation token)
			throws CleartkExtractorException {
		
		ArrayList<Feature> features = new ArrayList<Feature>();
		if (!word_val.isEmpty()){
			Float[] num = word_val.get(token.getCoveredText());
			if(num != null){
				for(int i=0;i<num.length;i++){
					features.add(new Feature(num[i]));
				}
			}else{
				for(int i=0;i<this.default_val.length;i++){
					features.add(new Feature(this.default_val[i]));
				}
			}
		}
		
		return Collections.synchronizedList(features);
	}

}

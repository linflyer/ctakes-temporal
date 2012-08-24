package org.apache.ctakes.feature.extractor;

import java.util.Collections;
import java.util.List;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.feature.extractor.CleartkExtractorException;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;
import org.uimafit.util.JCasUtil;

import edu.mayo.bmi.uima.core.type.syntax.Chunk_Type;
import edu.mayo.bmi.uima.core.type.syntax.Chunk;

public class PhraseExtractor implements SimpleFeatureExtractor {

	@Override
	public List<Feature> extract(JCas jCas, Annotation token)
			throws CleartkExtractorException {
		Feature feature = new Feature("NotNPVP");
		for (Chunk ck : JCasUtil.selectCovered(jCas, Chunk.class, token.getBegin(), token.getEnd() )) {
			String ckType = ck.getChunkType();
			System.out.println("**********find chunk:" + ckType + " :: "+ token.getCoveredText());
			if(ckType.equals("NP")){
				feature = new Feature("NP");
				return Collections.singletonList(feature);
			}else if(ckType.equals("VP")){
				feature = new Feature("VP");
				return Collections.singletonList(feature);
			}
		}
//		for (VP vp : JCasUtil.selectCovered(jCas, VP.class, token.getBegin(), token.getEnd() )) {
//			System.out.println("**********find VP:" + vp.getCoveredText()+" :: "+ token.toString());
//			feature = new Feature("VP");
//			return Collections.singletonList(feature);
//		}
		return Collections.singletonList(feature);
	}

}

package org.apache.ctakes.temporal.eval;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCopier;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.util.JCasUtil;

public class AnnotationCopier extends JCasAnnotator_ImplBase {

  public static AnalysisEngineDescription getDescription(
      String sourceView,
      String targetView,
      Class<? extends TOP> annotationClass) throws ResourceInitializationException {
    return AnalysisEngineFactory.createPrimitiveDescription(
        AnnotationCopier.class,
        PARAM_SOURCE_VIEW,
        sourceView,
        PARAM_TARGET_VIEW,
        targetView,
        PARAM_ANNOTATION_CLASS,
        annotationClass);
  }

  public static final String PARAM_SOURCE_VIEW = "SourceView";

  @ConfigurationParameter(name = PARAM_SOURCE_VIEW, mandatory = true)
  private String sourceView;

  public static final String PARAM_TARGET_VIEW = "TargetView";

  @ConfigurationParameter(name = PARAM_TARGET_VIEW, mandatory = true)
  private String targetView;

  public static final String PARAM_ANNOTATION_CLASS = "AnnotationClass";

  @ConfigurationParameter(name = PARAM_ANNOTATION_CLASS, mandatory = true)
  private Class<? extends TOP> annotationClass;

  @Override
  public void process(JCas jCas) throws AnalysisEngineProcessException {
    JCas sourceView, targetView;
    try {
      sourceView = jCas.getView(this.sourceView);
      targetView = jCas.getView(this.targetView);
    } catch (CASException e) {
      throw new AnalysisEngineProcessException(e);
    }
    CasCopier copier = new CasCopier(sourceView.getCas(), targetView.getCas());
    for (TOP annotation : JCasUtil.select(sourceView, this.annotationClass)) {
      TOP copy = (TOP) copier.copyFs(annotation);
      // CasCopier does not change sofa of annotation; without the code below, you get the error:
      // the Annotation "..." is over view "GoldView" and cannot be added to indexes associated
      // with the different view "_InitialView".
      Feature sofaFeature = copy.getType().getFeatureByBaseName(CAS.FEATURE_BASE_NAME_SOFA);
      if (sofaFeature != null) {
        copy.setFeatureValue(sofaFeature, targetView.getSofa());
      }
      copy.addToIndexes();
    }

  }

}
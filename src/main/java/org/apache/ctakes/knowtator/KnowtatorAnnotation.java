package org.apache.ctakes.knowtator;

import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;

/**
 * Represents a Knowtator annotation.
 */
public class KnowtatorAnnotation {
  /**
   * The unique identifier assigned to this annotation by Knowtator
   */
  public String id;

  /**
   * The character offsets of this annotation (or <code>null</code> if the annotation is not
   * associated with a span of text).
   */
  public Span span;

  /**
   * The type (or "class") of annotation
   */
  public String type;

  /**
   * The string-valued annotation attributes
   */
  public Map<String, String> stringSlots = new HashMap<String, String>();

  /**
   * The boolean-valued annotation attributes
   */
  public Map<String, Boolean> booleanSlots = new HashMap<String, Boolean>();

  /**
   * The annotation-valued annotation attributes (i.e. links between annotations)
   */
  public Map<String, KnowtatorAnnotation> annotationSlots = new HashMap<String, KnowtatorAnnotation>();

  /**
   * Construct a new KnowtatorAnnotation. (Not publicly available.)
   */
  KnowtatorAnnotation() {
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        this.id,
        this.span,
        this.type,
        this.stringSlots,
        this.booleanSlots,
        this.annotationSlots);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    KnowtatorAnnotation that = (KnowtatorAnnotation) obj;
    return Objects.equal(this.id, that.id) && Objects.equal(this.span, that.span)
        && Objects.equal(this.type, that.type) && Objects.equal(this.stringSlots, that.stringSlots)
        && Objects.equal(this.booleanSlots, that.booleanSlots)
        && Objects.equal(this.annotationSlots, that.annotationSlots);
  }

  @Override
  public String toString() {
    ToStringHelper builder = Objects.toStringHelper(this);
    builder.add("id", this.id);
    builder.add("span", this.span);
    builder.add("type", this.type);
    builder.add("stringSlots", this.stringSlots);
    builder.add("booleanSlots", this.booleanSlots);
    builder.add("mentionSlots", this.annotationSlots);
    return builder.toString();
  }

  /**
   * Represents the character offsets of a Knowtator annotation.
   */
  public static class Span {
    /**
     * The offset of the first character in the text span.
     */
    public int begin;

    /**
     * The offset immediately after the last character in the text span.
     */
    public int end;

    /**
     * The text spanned by the given character offsets.
     */
    public String text;

    /**
     * Construct a new Span. (Not publicly available.)
     */
    Span() {
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(this.begin, this.end, this.text);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || obj.getClass() != this.getClass()) {
        return false;
      }
      Span that = (Span) obj;
      return Objects.equal(this.begin, that.begin) && Objects.equal(this.end, that.end)
          && Objects.equal(this.text, that.text);
    }

    @Override
    public String toString() {
      ToStringHelper builder = Objects.toStringHelper(this);
      builder.add("begin", this.begin);
      builder.add("end", this.end);
      builder.add("text", this.text);
      return builder.toString();
    }

  }

}
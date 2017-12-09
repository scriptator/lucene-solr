/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.search.similarities;


import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modification of the classical BM25 similarity measure:
 * <p>
 * TODO
 * Your task is to modify the score calculation in this BM25Similarity class
 * and create a new similarity class called BM25ModSimilarity. You then use it
 * in your index by modifying the configurations.
 * <p>
 * for 15 points: Lv, Yuanhua, and ChengXiang Zhai. ”When documents are very long, bm25 fails!.” Proceedings of SIGIR 2011. link
 * for 30 points: Lipani, Aldo, et al. ”Verboseness Fission for BM25 Document Length Normal- ization.” Proceedings of ICTIR 2015. link
 */
public class BM25ModSimilarity extends Similarity {
  private final float k1;

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


  /**
   * BM25 with the supplied parameter values.
   *
   * @param k1 Controls non-linear term frequency normalization (saturation).
   * @throws IllegalArgumentException if {@code k1} is infinite or negative, or if {@code b} is
   *                                  not within the range {@code [0..1]}
   */
  public BM25ModSimilarity(float k1) {
    if (Float.isFinite(k1) == false || k1 < 0) {
      throw new IllegalArgumentException("illegal k1 value: " + k1 + ", must be a non-negative finite value");
    }
    this.k1 = k1;
  }

  /**
   * BM25 with these default values:
   * <ul>
   * <li>{@code k1 = 1.2}</li>
   * <li>{@code b = 0.75}</li>
   * </ul>
   */
  public BM25ModSimilarity() {
    this(1.2f);
  }

  /**
   * Implemented as <code>log(1 + (docCount - docFreq + 0.5)/(docFreq + 0.5))</code>.
   */
  protected float idf(long docFreq, long docCount) {
    return (float) Math.log(1 + (docCount - docFreq + 0.5D) / (docFreq + 0.5D));
  }

  /**
   * Implemented as <code>1 / (distance + 1)</code>.
   */
  protected float sloppyFreq(int distance) {
    return 1.0f / (distance + 1);
  }

  /**
   * The default implementation returns <code>1</code>
   */
  protected float scorePayload(int doc, int start, int end, BytesRef payload) {
    return 1;
  }

  /**
   * The default implementation computes the average as <code>sumTotalTermFreq / docCount</code>,
   * or returns <code>1</code> if the index does not store sumTotalTermFreq:
   * any field that omits frequency information).
   */
  protected float avgFieldLength(CollectionStatistics collectionStats) {
    final long sumTotalTermFreq = collectionStats.sumTotalTermFreq();
    if (sumTotalTermFreq <= 0) {
      return 1f;       // field does not exist, or stat is unsupported
    } else {
      final long docCount = collectionStats.docCount() == -1 ? collectionStats.maxDoc() : collectionStats.docCount();
      return (float) (sumTotalTermFreq / (double) docCount);
    }
  }

  // calculate mean average term frequency
  protected double computeMAvgTf(long docCount, NumericDocValues norms) throws IOException {
    double sum = 0;
    while (norms.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
      sum += unpackAvgTf(norms.longValue());
    }
    return sum / docCount;
  }

  /**
   * True if overlap tokens (tokens with a position of increment of zero) are
   * discounted from the document's length.
   */
  protected boolean discountOverlaps = true;

  /**
   * Sets whether overlap tokens (Tokens with 0 position increment) are
   * ignored when computing norm.  By default this is true, meaning overlap
   * tokens do not count when computing norms.
   */
  public void setDiscountOverlaps(boolean v) {
    discountOverlaps = v;
  }

  /**
   * Returns true if overlap tokens are discounted from the document's length.
   *
   * @see #setDiscountOverlaps
   */
  public boolean getDiscountOverlaps() {
    return discountOverlaps;
  }

  /**
   * Pack norm and avgTf into one long
   */
  protected long packNormAndAvgTf(int norm, float avgTf) {
    return ((long) norm << 32) | Float.floatToIntBits(avgTf);
  }

  /**
   * Unpack norm from packed value packed with packNormAndAvgTf
   */
  protected int unpackNorm(long packed) {
    return (int) (packed >> 32);
  }

  /**
   * Unpack norm from packed value packed with packNormAndAvgTf
   */
  protected float unpackAvgTf(long packed) {
    return Float.intBitsToFloat((int) packed);
  }

  @Override
  public final long computeNorm(FieldInvertState state) {
    final int norm = discountOverlaps ? state.getLength() - state.getNumOverlap() : state.getLength();
    final float avgTf = 1f * norm / state.getUniqueTermCount();
    // pack docLen and avgTf into one long
    long packed =  packNormAndAvgTf(norm, avgTf);
    log.info("Name: " + state.getName() + ", Norm: " + norm + ", avgTf: " + avgTf + ", packed: " + packed);
    return packed;
  }

  /**
   * Computes a score factor for a simple term and returns an explanation
   * for that score factor.
   * <p>
   * <p>
   * The default implementation uses:
   * <p>
   * <pre class="prettyprint">
   * idf(docFreq, docCount);
   * </pre>
   * <p>
   * Note that {@link CollectionStatistics#docCount()} is used instead of
   * {@link org.apache.lucene.index.IndexReader#numDocs() IndexReader#numDocs()} because also
   * {@link TermStatistics#docFreq()} is used, and when the latter
   * is inaccurate, so is {@link CollectionStatistics#docCount()}, and in the same direction.
   * In addition, {@link CollectionStatistics#docCount()} does not skew when fields are sparse.
   *
   * @param collectionStats collection-level statistics
   * @param termStats       term-level statistics for the term
   * @return an Explain object that includes both an idf score factor
   * and an explanation for the term.
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
    final long df = termStats.docFreq();
    final long docCount = collectionStats.docCount() == -1 ? collectionStats.maxDoc() : collectionStats.docCount();
    final float idf = idf(df, docCount);
    return Explanation.match(idf, "idf, computed as log(1 + (docCount - docFreq + 0.5) / (docFreq + 0.5)) from:",
        Explanation.match(df, "docFreq"),
        Explanation.match(docCount, "docCount"));
  }

  /**
   * Computes a score factor for a phrase.
   * <p>
   * <p>
   * The default implementation sums the idf factor for
   * each term in the phrase.
   *
   * @param collectionStats collection-level statistics
   * @param termStats       term-level statistics for the terms in the phrase
   * @return an Explain object that includes both an idf
   * score factor for the phrase and an explanation
   * for each term.
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats[]) {
    double idf = 0d; // sum into a double before casting into a float
    List<Explanation> details = new ArrayList<>();
    for (final TermStatistics stat : termStats) {
      Explanation idfExplain = idfExplain(collectionStats, stat);
      details.add(idfExplain);
      idf += idfExplain.getValue();
    }
    return Explanation.match((float) idf, "idf(), sum of:", details);
  }

  @Override
  public final SimWeight computeWeight(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    Explanation idf = termStats.length == 1 ? idfExplain(collectionStats, termStats[0]) : idfExplain(collectionStats, termStats);
    float avgdl = avgFieldLength(collectionStats);
    final long docCount = collectionStats.docCount() == -1 ? collectionStats.maxDoc() : collectionStats.docCount();

    return new BM25ModStats(collectionStats.field(), boost, idf, docCount, avgdl);
  }

  @Override
  public final SimScorer simScorer(SimWeight stats, LeafReaderContext context) throws IOException {
    BM25ModStats bm25stats = (BM25ModStats) stats;
    return new BM25ModDocScorer(bm25stats,
        context.reader().getNumericDocValues(bm25stats.field),
        context.reader().getNormValues(bm25stats.field));
  }

  private class BM25ModDocScorer extends SimScorer {
    private final BM25ModStats stats;
    private final float weightValue; // boost * idf * (k1 + 1)
    private final double mAvgTf;     // mean average term frequencies
    private final NumericDocValues norms;
    private final NumericDocValues avgTfs;

    BM25ModDocScorer(BM25ModStats stats, NumericDocValues avgTfs,
                     NumericDocValues norms) throws IOException {
      log.info("Scorer initialized");
      this.stats = stats;
      this.weightValue = stats.weight * (k1 + 1);
      this.avgTfs = avgTfs;
      this.norms = norms;

      mAvgTf = computeMAvgTf(stats.docCount, norms);
    }

    @Override
    public float score(int doc, float freq) throws IOException {
//      log.info("Score called: " + doc + ", " + freq);
      // if there are no norms, we act as if b=0
      double norm;
      double avgTf;
      if (norms == null) {
        log.warn("No norm available, setting B to 1");
        norm = 1;
        avgTf = 1;
      } else {
        if (norms.advanceExact(doc)) {
          norm = unpackNorm(norms.longValue());
          avgTf = unpackAvgTf(norms.longValue());
        } else {
          log.warn("No value for norm available");
          norm = 1;
          avgTf = 1;
        }
      }

      double bva = 1 / (mAvgTf*mAvgTf) * avgTf + (1 - 1 / mAvgTf) * norm / stats.avgdl;
      log.info("Computed bva value " + bva + " for document " + doc);
      return (float) (weightValue * freq / (freq + k1 * bva));
    }

    @Override
    public Explanation explain(int doc, Explanation freq) throws IOException {
      return explainScore(doc, freq, stats, norms);
    }

    @Override
    public float computeSlopFactor(int distance) {
      return sloppyFreq(distance);
    }

    @Override
    public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
      return scorePayload(doc, start, end, payload);
    }
  }

  /**
   * Collection statistics for the BM25 model.
   */
  private static class BM25ModStats extends SimWeight {
    /**
     * BM25's idf
     */
    private final Explanation idf;
    /**
     * The average document length.
     */
    private final float avgdl;
    /**
     * query boost
     */
    private final float boost;
    /**
     * weight (idf * boost)
     */
    private final float weight;
    /**
     * field name, for pulling norms
     */
    private final String field;
    /**
     * Numer of documents
     */
    private final long docCount;

    BM25ModStats(String field, float boost, Explanation idf, long docCount, float avgdl) {
      this.field = field;
      this.boost = boost;
      this.idf = idf;
      this.docCount = docCount;
      this.avgdl = avgdl;
      this.weight = idf.getValue() * boost;
    }
  }

  private Explanation explainTFNorm(int doc, Explanation freq, BM25ModStats stats, NumericDocValues norms) throws IOException {
    List<Explanation> subs = new ArrayList<>();
    subs.add(freq);
    subs.add(Explanation.match(k1, "parameter k1"));
    if (norms == null) {
      subs.add(Explanation.match(0, "parameter b (norms omitted for field)"));
      return Explanation.match(
          (freq.getValue() * (k1 + 1)) / (freq.getValue() + k1),
          "tfNorm, computed as (freq * (k1 + 1)) / (freq + k1) from:", subs);
    } else {
      byte norm;
      if (norms.advanceExact(doc)) {
        norm = (byte) norms.longValue();
      } else {
        norm = 0;
      }
      subs.add(Explanation.match(stats.avgdl, "avgFieldLength"));
      // TODO fix this calculation again
      return Explanation.match(
          1,
//          (freq.getValue() * (k1 + 1)) / (freq.getValue() + k1 * (1 - b + b * doclen / stats.avgdl)),
          "tfNorm, computed as (freq * (k1 + 1)) / (freq + k1 * (1 - b + b * fieldLength / avgFieldLength)) from:", subs);
    }
  }

  private Explanation explainScore(int doc, Explanation freq, BM25ModStats stats, NumericDocValues norms) throws IOException {
    Explanation boostExpl = Explanation.match(stats.boost, "boost");
    List<Explanation> subs = new ArrayList<>();
    if (boostExpl.getValue() != 1.0f)
      subs.add(boostExpl);
    subs.add(stats.idf);
    Explanation tfNormExpl = explainTFNorm(doc, freq, stats, norms);
    subs.add(tfNormExpl);
    return Explanation.match(
        boostExpl.getValue() * stats.idf.getValue() * tfNormExpl.getValue(),
        "score(doc=" + doc + ",freq=" + freq + "), product of:", subs);
  }

  @Override
  public String toString() {
    return "BM25(k1=" + k1 + ")";
  }

  /**
   * Returns the <code>k1</code> parameter
   *
   * @see #BM25ModSimilarity(float)
   */
  public final float getK1() {
    return k1;
  }

}

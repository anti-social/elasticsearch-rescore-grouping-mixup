/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package company.evo.elasticsearch.rescore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.fielddata.SortedBinaryDocValues;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.rescore.RescoreContext;
import org.elasticsearch.search.rescore.Rescorer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GroupingMixupRescorer implements Rescorer {
    public static final String POSITION_PARAMETER_NAME = "_pos";

    static final GroupingMixupRescorer INSTANCE = new GroupingMixupRescorer();

    private static final Comparator<ScoreDoc> DOC_COMPARATOR = Comparator.comparingInt((d) -> d.doc);

    private static final Comparator<ScoreDoc> SCORE_DOC_COMPARATOR = (a, b) -> {
        if (a.score > b.score) {
            return -1;
        }
        else if (a.score < b.score) {
            return 1;
        }
        return a.doc - b.doc;
    };

    private final Logger logger = LogManager.getLogger(getClass());

    @Override
    public TopDocs rescore(TopDocs topDocs, IndexSearcher searcher, RescoreContext rescoreContext)
            throws IOException
    {
        assert rescoreContext != null;
        if (topDocs == null || topDocs.totalHits == 0 || topDocs.scoreDocs.length == 0) {
            return topDocs;
        }

        final Context rescoreCtx = (Context) rescoreContext;

        ScoreDoc[] hits = topDocs.scoreDocs;
        int windowSize = Math.min(rescoreCtx.getWindowSize(), hits.length);
        if (windowSize <= 0) {
            return topDocs;
        }
        Arrays.sort(hits, 0, windowSize, DOC_COMPARATOR);

        List<LeafReaderContext> readerContexts = searcher.getIndexReader().leaves();
        int currentReaderIx = -1;
        int currentReaderEndDoc = 0;
        LeafReaderContext currentReaderContext = null;
        SortedBinaryDocValues groupValues = null;
        SearchScript declineScript = null;

        final Map<BytesRef, Integer> groupPositions = new HashMap<>();

        BytesRefBuilder groupValueBuilder = new BytesRefBuilder();

        for (int hitIx = 0; hitIx < windowSize; hitIx++) {
            ScoreDoc hit = hits[hitIx];
            LeafReaderContext prevReaderContext = currentReaderContext;

            // find segment that contains current document
            while (hit.doc >= currentReaderEndDoc) {
                currentReaderIx++;
                currentReaderContext = readerContexts.get(currentReaderIx);
                currentReaderEndDoc = currentReaderContext.docBase + currentReaderContext.reader().maxDoc();
            }

            int docId = hit.doc - currentReaderContext.docBase;
            if (currentReaderContext != prevReaderContext) {
                groupValues = rescoreCtx.groupingField
                        .load(currentReaderContext)
                        .getBytesValues();
                declineScript = rescoreCtx.declineScript.newInstance(currentReaderContext);
            }
            if (groupValues.advanceExact(docId)) {
                groupValueBuilder.copyBytes(groupValues.nextValue());
            } else {
                groupValueBuilder.clear();
            }
            BytesRef groupValue = groupValueBuilder.toBytesRef();
            int groupPos = groupPositions.compute(groupValue, (k, curPos) -> {
                if (curPos == null) {
                    return 0;
                }
                return ++curPos;
            });

            // Calculate new score
            declineScript.setDocument(docId);
            Map<String, Object> scriptParams = declineScript.getParams();
            scriptParams.put(POSITION_PARAMETER_NAME, (double) groupPos);
            hit.score = hit.score * (float) declineScript.runAsDouble();
        }

        // Sort hits by new scores
        Arrays.sort(hits, 0, windowSize, SCORE_DOC_COMPARATOR);
        float minRescoredScore = hits[windowSize - 1].score;

        // Decrease scores for hits that were not rescored.
        // We must do that to satisfy elasticsearch's assertion
        if (hits.length > windowSize) {
            float maxNonRescoredScore = hits[windowSize].score;
            float deltaScore = maxNonRescoredScore - minRescoredScore;
            for (int i = windowSize; i < hits.length; i++) {
                ScoreDoc hit = hits[i];
                hit.score -= deltaScore;
            }
        }

        return new TopDocs(topDocs.totalHits, hits, hits[hits.length - 1].score);
    }

    @Override
    public Explanation explain(int topLevelDocId, IndexSearcher searcher, RescoreContext rescoreContext,
                               Explanation sourceExplanation) {
        // We cannot explain new scores because we only have single document at this point
        return sourceExplanation;
    }

    @Override
        public void extractTerms(IndexSearcher searcher, RescoreContext rescoreContext, Set<Term> termsSet) {
            // Since we don't use queries there are no terms to extract.
    }

    static class Context extends RescoreContext {
        private IndexFieldData<?> groupingField;
        private final SearchScript.LeafFactory declineScript;

        Context(int windowSize, IndexFieldData<?> groupingField, SearchScript.LeafFactory declineScript) {
            super(windowSize, GroupingMixupRescorer.INSTANCE);
            this.groupingField = groupingField;
            this.declineScript = declineScript;
        }
    }
}

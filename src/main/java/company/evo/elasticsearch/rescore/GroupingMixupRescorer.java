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
        int currentReaderIx = 0;
        LeafReaderContext currentReaderContext = readerContexts.get(currentReaderIx);

        SortedBinaryDocValues fieldValues = rescoreCtx.groupingField
                .load(currentReaderContext)
                .getBytesValues();

        final Map<Integer, BytesRef> groupValues = new HashMap<>();
        Map<Integer, LeafReaderContext> docLeafContexts = new HashMap<>();

        BytesRefBuilder valueBuilder = new BytesRefBuilder();

        for (int hitIx = 0; hitIx < windowSize; hitIx++) {
            ScoreDoc hit = hits[hitIx];
            LeafReaderContext prevReaderContext = currentReaderContext;

            // find segment that contains current document
            int docId = hit.doc - currentReaderContext.docBase;
            while (docId >= currentReaderContext.reader().maxDoc()) {
                currentReaderIx++;
                currentReaderContext = readerContexts.get(currentReaderIx);
                docId = hit.doc - currentReaderContext.docBase;
            }

            docLeafContexts.put(hit.doc, currentReaderContext);

            if (currentReaderContext != prevReaderContext) {
                fieldValues = rescoreCtx.groupingField
                        .load(currentReaderContext)
                        .getBytesValues();
            }

            if (fieldValues.advanceExact(docId)) {
                valueBuilder.copyBytes(fieldValues.nextValue());
            } else {
                valueBuilder.clear();
            }
            groupValues.put(hit.doc, valueBuilder.toBytesRef());
        }

        // Sort by group value
        Arrays.sort(hits, 0, windowSize, (a, b) -> {
            int cmp = groupValues.get(a.doc).compareTo(groupValues.get(b.doc));
            if (cmp == 0) {
                return SCORE_DOC_COMPARATOR.compare(a, b);
            }
            return cmp;
        });

        // Calculate new scores
        int pos = 0;
        float minOrigScore = hits[windowSize - 1].score;
        BytesRef curGroupValue = null, prevGroupValue = null;
        for (int i = 0; i < windowSize; i++) {
            ScoreDoc hit = hits[i];
            curGroupValue = groupValues.get(hit.doc);
            if (!curGroupValue.equals(prevGroupValue)) {
                pos = 0;
            }

            LeafReaderContext leafContext = docLeafContexts.get(hit.doc);
            SearchScript boostScript = rescoreCtx.declineScript.newInstance(leafContext);
            boostScript.setDocument(hit.doc - leafContext.docBase);
            boostScript.setNextVar("_pos", pos);
            hit.score = hit.score * (float) boostScript.runAsDouble();

            pos++;
            prevGroupValue = curGroupValue;
        }

        // Decrease scores for hits that were not rescored.
        // We must do that to satisfy elasticsearch's assertion
        float lastDeltaScore = minOrigScore - hits[windowSize - 1].score;
        if (lastDeltaScore > 0.0F) {
            for (int i = windowSize; i < hits.length; i++) {
                ScoreDoc hit = hits[i];
                hit.score -= lastDeltaScore;
            }
        }

        // Sort hits by new scores
        Arrays.sort(hits, SCORE_DOC_COMPARATOR);

        return new TopDocs(topDocs.totalHits, hits, hits[0].score);
    }

    @Override
    public Explanation explain(int topLevelDocId, IndexSearcher searcher, RescoreContext rescoreContext,
                               Explanation sourceExplanation) throws IOException {
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

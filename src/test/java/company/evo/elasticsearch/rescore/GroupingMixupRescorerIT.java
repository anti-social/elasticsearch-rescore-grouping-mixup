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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.search.rescore.RescorePhase;

import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_SHARDS;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertOrderedSearchHits;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.SUITE)
public class GroupingMixupRescorerIT extends ESIntegTestCase {
    private static final String DEBUG_SEP = "======================";

    public void testEmptyIndex() throws IOException {
        assertAcked(prepareCreate("test")
                .setSettings(Settings.builder().put(SETTING_NUMBER_OF_SHARDS, 1))
                .addMapping("product",
                        jsonBuilder()
                        .startObject().startObject("product").startObject("properties")
                        .startObject("company_id")
                                .field("type", "integer")
                        .endObject()
                        .endObject().endObject().endObject()));
        ensureYellow();
        refresh();

        SearchResponse resp;
        MatchAllQueryBuilder queryBuilder = QueryBuilders.matchAllQuery();

        resp = client().prepareSearch()
                .setQuery(queryBuilder)
                .setRescorer(
                        new GroupingMixupRescorerBuilder(
                                "company_id",
                                new Script(
                                        ScriptType.INLINE,
                                        "grouping_mixup_scripts",
                                        "position_recip",
                                        Collections.emptyMap()))
                        .windowSize(5))
                .execute()
                .actionGet();
        assertHitCount(resp, 0);
    }

    public void testRescoring() throws IOException {
        assertAcked(prepareCreate("test")
                .setSettings(Settings.builder().put(SETTING_NUMBER_OF_SHARDS, 1))
                .addMapping("product",
                        jsonBuilder().startObject().startObject("product").startObject("properties")
                        .startObject("name")
                                .field("type", "text")
                                .field("analyzer", "whitespace")
                        .endObject()
                        .startObject("company_id")
                                .field("type", "integer")
                        .endObject()
                        .endObject().endObject().endObject()));

        client().prepareIndex("test", "product", "1")
            .setSource(
                    "name", "the quick brown fox",
                    "company_id", 1)
            .execute()
            .actionGet();
        client().prepareIndex("test", "product", "2")
            .setSource(
                    "name", "the quick lazy huge fox jumps over the tree",
                    "company_id", 2)
            .execute()
            .actionGet();
        client().prepareIndex("test", "product", "3")
            .setSource(
                    "name", "quick huge brown fox",
                    "company_id", 1)
            .execute()
            .actionGet();
        client().prepareIndex("test", "product", "4")
            .setSource("name", "the quick lonely fox")
            .execute()
            .actionGet();
        ensureYellow();
        refresh();

        SearchResponse resp;
        MatchQueryBuilder queryBuilder = QueryBuilders
                .matchQuery("name", "the quick brown");

        // No rescore
        resp = client().prepareSearch()
                .setQuery(queryBuilder)
                .execute()
                .actionGet();
        assertHitCount(resp, 4);
        org.elasticsearch.search.SearchHits hits;
        hits = resp.getHits();
        logger.info("{} {}", hits.getAt(0).getId(), hits.getAt(0).getScore());
        logger.info("{} {}", hits.getAt(1).getId(), hits.getAt(1).getScore());
        logger.info("{} {}", hits.getAt(2).getId(), hits.getAt(2).getScore());
        logger.info("{} {}", hits.getAt(3).getId(), hits.getAt(3).getScore());
        logger.info(DEBUG_SEP);
        assertOrderedSearchHits(resp, "1", "3", "4", "2");

        // With rescoring
        resp = client().prepareSearch()
                .setQuery(queryBuilder)
                .setRescorer(
                        new GroupingMixupRescorerBuilder(
                                "company_id",
                                new Script(
                                        ScriptType.INLINE,
                                        "grouping_mixup_scripts",
                                        "position_recip",
                                        Collections.emptyMap()))
                        .windowSize(5))
                .execute()
                .actionGet();
        assertHitCount(resp, 4);
        hits = resp.getHits();
        logger.info("{} {}", hits.getAt(0).getId(), hits.getAt(0).getScore());
        logger.info("{} {}", hits.getAt(1).getId(), hits.getAt(1).getScore());
        logger.info("{} {}", hits.getAt(2).getId(), hits.getAt(2).getScore());
        logger.info("{} {}", hits.getAt(3).getId(), hits.getAt(3).getScore());
        logger.info(DEBUG_SEP);
        assertOrderedSearchHits(resp, "1", "4", "2", "3");

        // Rescoring with script params
        Map<String, Object> scriptParams = new HashMap<>();
        scriptParams.put("m", 1.0);
        scriptParams.put("a", 1.0);
        scriptParams.put("b", 1.1);
        scriptParams.put("c", 0.090909);
        resp = client().prepareSearch()
                .setQuery(queryBuilder)
                .setRescorer(
                        new GroupingMixupRescorerBuilder(
                                "company_id",
                                new Script(
                                        ScriptType.INLINE,
                                        "grouping_mixup_scripts",
                                        "position_recip",
                                        scriptParams))
                    .windowSize(5))
                .execute()
                .actionGet();
        assertHitCount(resp, 4);
        hits = resp.getHits();
        logger.info("{} {}", hits.getAt(0).getId(), hits.getAt(0).getScore());
        logger.info("{} {}", hits.getAt(1).getId(), hits.getAt(1).getScore());
        logger.info("{} {}", hits.getAt(2).getId(), hits.getAt(2).getScore());
        logger.info("{} {}", hits.getAt(3).getId(), hits.getAt(3).getScore());
        logger.info(DEBUG_SEP);
        assertOrderedSearchHits(resp, "1", "4", "3", "2");

        // Small size
        resp = client().prepareSearch()
                .setQuery(queryBuilder)
                .setSize(2)
                .setRescorer(
                        new GroupingMixupRescorerBuilder(
                                "company_id",
                                new Script(
                                        ScriptType.INLINE,
                                        "grouping_mixup_scripts",
                                        "position_recip",
                                        Collections.emptyMap()))
                        .windowSize(5))
                .execute()
                .actionGet();
        assertHitCount(resp, 4);
        logger.info("{} {}", hits.getAt(0).getId(), hits.getAt(0).getScore());
        logger.info("{} {}", hits.getAt(1).getId(), hits.getAt(1).getScore());
        logger.info(DEBUG_SEP);
        assertOrderedSearchHits(resp, "1", "4");

        // Small rescoring window
        resp = client().prepareSearch()
                .setQuery(queryBuilder)
                .setRescorer(
                        new GroupingMixupRescorerBuilder(
                                "company_id",
                                new Script(
                                        ScriptType.INLINE,
                                        "grouping_mixup_scripts",
                                        "position_recip",
                                        Collections.emptyMap()))
                        .windowSize(3))
                .execute()
                .actionGet();
        assertHitCount(resp, 4);
        logger.info("{} {}", hits.getAt(0).getId(), hits.getAt(0).getScore());
        logger.info("{} {}", hits.getAt(1).getId(), hits.getAt(1).getScore());
        logger.info("{} {}", hits.getAt(2).getId(), hits.getAt(2).getScore());
        logger.info("{} {}", hits.getAt(3).getId(), hits.getAt(3).getScore());
        logger.info(DEBUG_SEP);
        assertOrderedSearchHits(resp, "1", "4", "3", "2");
    }
}

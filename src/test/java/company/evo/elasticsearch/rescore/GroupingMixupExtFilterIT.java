package company.evo.elasticsearch.rescore;

import company.evo.elasticsearch.GroupingMixupExtBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.test.ESIntegTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.elasticsearch.cluster.metadata.IndexMetadata.SETTING_NUMBER_OF_SHARDS;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertOrderedSearchHits;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertRequestBuilderThrows;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.SUITE)
public class GroupingMixupExtFilterIT extends ESIntegTestCase {
    private final static int NUMBER_OF_SHARDS = 2;

    public void testEmpty() throws IOException {
        createIndex(NUMBER_OF_SHARDS);

        var resp = rescoreSearchRequest().get();
        assertHitCount(resp, 0);
    }

    public void testRescore() throws IOException {
        createIndex(NUMBER_OF_SHARDS);
        populateIndex();

        var resp = rescoreSearchRequest().get();
        assertHitCount(resp, 6);
        assertOrderedSearchHits(resp, "101", "201", "301", "103", "304", "204");
        GroupingMixupRescorerIT.assertOrderedSearchHitScores(
            resp, 2.1F, 1.6F, 1.0F, 0.95F, 0.4F, 0.2F
        );
    }

    public void testRescoreWithWindowSize() throws IOException {
        createIndex(NUMBER_OF_SHARDS);
        populateIndex();

        var resp = rescoreSearchRequest(3, 1000, true).get();
        assertHitCount(resp, 6);
        assertOrderedSearchHits(resp, "101", "201", "103", "301", "304", "204");
        GroupingMixupRescorerIT.assertOrderedSearchHitScores(
            resp, 2.1F, 1.6F, 0.95F, 1.0F, 0.8F, 0.4F
        );
    }

    public void testRescoreWithShardSize() throws IOException {
        createIndex(NUMBER_OF_SHARDS);
        populateIndex();

        var resp = rescoreSearchRequest(10_000, 2, false).setSize(2).get();
        assertHitCount(resp, 6);
        assertOrderedSearchHits(resp, "101", "201", "103", "304");
        GroupingMixupRescorerIT.assertOrderedSearchHitScores(
            resp, 2.1F, 1.6F, 0.95F, 0.8F
        );
    }

    public void testRescoreMissingScriptFields() throws IOException {
        createIndex(NUMBER_OF_SHARDS);
        populateIndex();

        var request = rescoreSearchRequest(
            10_000,
            1000,
            true,
            new GroupingMixupExtBuilder.RescoreScript(
                new Script(
                    ScriptType.INLINE,
                    "painless",
                    "if (doc['rank'].value > 1) return 1.0 / (_pos + 1); else return 1.0;",
                    Collections.emptyMap()
                )
            )
        );
        assertRequestBuilderThrows(request, RestStatus.BAD_REQUEST);
    }

    public void testRescoreWithScriptFields() throws IOException {
        createIndex(NUMBER_OF_SHARDS);
        populateIndex();

        var resp = rescoreSearchRequest(
            10_000,
            1000,
            true,
            new GroupingMixupExtBuilder.RescoreScript(
                new Script(
                    ScriptType.INLINE,
                    "painless",
                    "if (doc['rank'].value > 1) return 1.0 / (_pos + 1); else return 1.0;",
                    Collections.emptyMap()
                ),
                List.of("rank")
            )
        ).get();
        assertHitCount(resp, 6);
        assertOrderedSearchHits(resp, "101", "201", "301", "103", "304", "204");
        GroupingMixupRescorerIT.assertOrderedSearchHitScores(
            resp, 2.1F, 1.6F, 1.0F, 0.95F, 0.8F, 0.4F
        );
    }

    public void testRescorePagination() throws IOException {
        createIndex(NUMBER_OF_SHARDS);
        populateIndex();

        var resp = rescoreSearchRequest().setFrom(2).setSize(2).get();
        assertHitCount(resp, 6);
        assertOrderedSearchHits(resp, "301", "103");
        GroupingMixupRescorerIT.assertOrderedSearchHitScores(resp, 1.0F, 0.95F);
    }

    public void testRescoreDisablePagination() throws IOException {
        createIndex(NUMBER_OF_SHARDS);
        populateIndex();

        var resp = rescoreSearchRequest(10_000, 1000, false).setSize(2).get();
        assertHitCount(resp, 6);
        assertOrderedSearchHits(resp, "101", "201", "301", "103", "304", "204");
        GroupingMixupRescorerIT.assertOrderedSearchHitScores(
            resp, 2.1F, 1.6F, 1.0F, 0.95F, 0.4F, 0.2F
        );
    }

    private SearchRequestBuilder rescoreSearchRequest() {
        return rescoreSearchRequest(10_000, 1000, true);
    }

    private SearchRequestBuilder rescoreSearchRequest(int windowSize, int shardSize, boolean pagination) {
        return rescoreSearchRequest(
            windowSize,
            shardSize,
            pagination,
            new GroupingMixupExtBuilder.RescoreScript(
                new Script(
                    ScriptType.INLINE,
                    "painless",
                    "return 1.0 / (_pos + 1);",
                    Collections.emptyMap()
                )
            )
        );
    }

    private SearchRequestBuilder rescoreSearchRequest(int windowSize, int shardSize, boolean pagination, GroupingMixupExtBuilder.RescoreScript rescoreScript) {
        return client().prepareSearch()
            .setSource(
                SearchSourceBuilder.searchSource()
                    .query(
                        QueryBuilders.functionScoreQuery(
                            ScoreFunctionBuilders.scriptFunction(
                                new Script("return doc['rank'].value;")
                            )
                        )
                    )
                    .ext(
                        List.of(
                            new GroupingMixupExtBuilder(
                                "company_id", rescoreScript
                            )
                                .windowSize(windowSize)
                                .shardSize(shardSize)
                                .pagination(pagination)
                        )
                    )
            );
    }

    private void createIndex(int numShards) throws IOException {
        assertAcked(
            prepareCreate("test")
                .setSettings(Settings.builder().put(SETTING_NUMBER_OF_SHARDS, numShards))
                .addMapping("product",
                    jsonBuilder()
                        .startObject()
                          .startObject("product")
                            .startObject("properties")
                              .startObject("name")
                                .field("type", "text")
                                .field("analyzer", "whitespace")
                              .endObject()
                              .startObject("company_id")
                                .field("type", "integer")
                              .endObject()
                              .startObject("rank")
                                .field("type", "float")
                              .endObject()
                            .endObject()
                          .endObject()
                        .endObject())
        );
        ensureYellow();
    }

    private void populateIndex() throws IOException {
        client().prepareIndex("test", "product", "101")
            .setSource(
                "name", "the quick brown fox",
                "company_id", 1,
                "rank", 2.1
            )
            .get();
        client().prepareIndex("test", "product", "103")
            .setSource(
                "name", "quick huge brown fox",
                "company_id", 1,
                "rank", 1.9
            )
            .get();
        refresh();
        // Ensure products are placed on different shards
        assertHitCount(
            client().prepareSearch()
                .setQuery(
                    QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termQuery("company_id", 1))
                )
                .setPreference("_shards:0")
                .get(),
        1
        );

        client().prepareIndex("test", "product", "201")
            .setSource(
                "name", "the quick lazy huge fox jumps over the tree",
                "company_id", 2,
                "rank", 1.6
            )
            .get();
        client().prepareIndex("test", "product", "204")
            .setSource(
                "name", "the brother of the quick lazy fox",
                "company_id", 2,
                "rank", 0.4
            )
            .get();
        refresh();
        // Ensure products are placed on different shards
        assertHitCount(
            client().prepareSearch()
                .setQuery(
                    QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termQuery("company_id", 2))
                )
                .setPreference("_shards:0")
                .get(),
            1
        );

        client().prepareIndex("test", "product", "301")
            .setSource(
                "name", "the quick lonely fox",
                "rank", 1.0
            )
            .get();
        client().prepareIndex("test", "product", "304")
            .setSource(
                "name", "another lonely fox",
                "rank", 0.8
            )
            .get();
        refresh();
        // Ensure products are placed on different shards
        assertHitCount(
            client().prepareSearch()
                .setQuery(
                    QueryBuilders.boolQuery()
                        .filter(
                            QueryBuilders.boolQuery()
                                .mustNot(QueryBuilders.existsQuery("company_id"))
                        )
                )
                .setPreference("_shards:0")
                .get(),
            1
        );

        ensureYellow();
    }
}

package company.evo.elasticsearch;

import company.evo.elasticsearch.rescore.DummyGroupingMixupRescorer;
import company.evo.elasticsearch.script.RescoreScript;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.internal.InternalSearchResponse;
import org.elasticsearch.search.profile.SearchProfileShardResults;
import org.elasticsearch.tasks.Task;

import java.util.Arrays;
import java.util.HashMap;

public class GroupingMixupFilter implements ActionFilter {
    private static final int DEFAULT_ORDER = 50;
    public static final Setting<Integer> MIXUP_RESCORE_FILTER_ORDER = Setting.intSetting(
        "mixup.rescore.filter.order", DEFAULT_ORDER, Setting.Property.NodeScope
    );

    private final ScriptService scriptService;
    private final int order;

    public GroupingMixupFilter(Settings settings, ScriptService scriptService) {
        this.scriptService = scriptService;
        this.order = MIXUP_RESCORE_FILTER_ORDER.get(settings);
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public <Request extends ActionRequest, Response extends ActionResponse> void apply(
        Task task,
        String action,
        Request request,
        ActionListener<Response> listener,
        ActionFilterChain<Request, Response> chain
    ) {
        if (!SearchAction.INSTANCE.name().equals(action)) {
            chain.proceed(task, action, request, listener);
            return;
        }

        final var searchRequest = (SearchRequest) request;
        var source = searchRequest.source();
        if (source == null) {
            source = SearchSourceBuilder.searchSource();
        }
        final var origSize = source.size();
        if (origSize == 0) {
            chain.proceed(task, action, request, listener);
            return;
        }
        final var size = origSize < 0 ? 10 : origSize;
        final var from = Math.max(source.from(), 0);

        final var searchExtensions = source.ext();
        final var searchExt = searchExtensions.stream()
                .filter(ext -> ext.getWriteableName().equals(GroupingMixupExtBuilder.NAME))
                .findFirst();
        if (searchExt.isEmpty()) {
            chain.proceed(task, action, request, listener);
            return;
        }

        final var mixupExt = (GroupingMixupExtBuilder) searchExt.get();
        final var groupFieldName = mixupExt.groupField();
        final var script = mixupExt.rescoreScript();
        RescoreScript.Factory rescoreScriptFactory = scriptService.compile(
                script.script,
                RescoreScript.CONTEXT
        );
        RescoreScript rescoreScript = rescoreScriptFactory.newInstance(script.script.getParams());

        source.from(0);
        source.size(Math.max(mixupExt.windowSize(), from + size));
        source.addRescorer(
            new DummyGroupingMixupRescorer.Builder()
                .shardSize(mixupExt.shardSize())
        );
        source.docValueField(groupFieldName);
        if (script.fields != null) {
            for (var scriptField : script.fields) {
                source.docValueField(scriptField);
            }
        }

        @SuppressWarnings("unchecked")
        final ActionListener<Response> rescoreListener = ActionListener.map(listener, (response) -> {
            final var resp = (SearchResponse) response;
            final var searchHits = resp.getHits();
            final var hits = searchHits.getHits();
            if (hits.length == 0) {
                return response;
            }

            final var currentPositions = new HashMap<Object, Integer>();

            // Rescore first window_size hits
            final var windowSize = Math.min(mixupExt.windowSize(), searchHits.getHits().length);
            var hitIx = 0;
            for (var hit : hits) {
                final var groupFieldValue = hit.field(groupFieldName).getValue();
                final int pos = currentPositions.compute(
                        groupFieldValue,
                        (k, v) -> v == null ? 1 : v + 1
                ) - 1;

                rescoreScript.setSearchHit(hit, pos);

                var newScore = (float) rescoreScript.execute() * hit.getScore();
                hit.score(newScore);
                hitIx++;
                if (hitIx == windowSize) {
                    break;
                }
            }

            Arrays.sort(hits, 0, hitIx, (h1, h2) -> Float.compare(h2.getScore(), h1.getScore()));
            if (mixupExt.pagination()) {
                return (Response) paginate(resp, hits, from, size);
            }
            return response;

        });

        chain.proceed(task, action, request, rescoreListener);
    }

    private SearchResponse paginate(
            SearchResponse response, SearchHit[] hits, int from, int size
    ) {
        final var totalHits = response.getHits().getTotalHits();
        final var page = from < hits.length ?
                Arrays.copyOfRange(hits, from, Math.min(from + size, hits.length)) :
                new SearchHit[0];

        final var pageResponse = new InternalSearchResponse(
                new SearchHits(page, totalHits, hits[0].getScore()),
                (InternalAggregations) response.getAggregations(),
                response.getSuggest(),
                new SearchProfileShardResults(response.getProfileResults()),
                response.isTimedOut(),
                response.isTerminatedEarly(),
                response.getNumReducePhases()
        );
        return new SearchResponse(
                pageResponse,
                response.getScrollId(),
                response.getTotalShards(),
                response.getSuccessfulShards(),
                response.getSkippedShards(),
                response.getTook().millis(),
                response.getShardFailures(),
                response.getClusters()
        );
    }
}

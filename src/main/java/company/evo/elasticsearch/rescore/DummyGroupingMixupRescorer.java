package company.evo.elasticsearch.rescore;

import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.search.rescore.RescoreContext;
import org.elasticsearch.search.rescore.Rescorer;
import org.elasticsearch.search.rescore.RescorerBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class DummyGroupingMixupRescorer implements Rescorer {
    static final DummyGroupingMixupRescorer INSTANCE = new DummyGroupingMixupRescorer();

    @Override
    public TopDocs rescore(TopDocs topDocs, IndexSearcher searcher, RescoreContext rescoreContext)
        throws IOException
    {
        assert rescoreContext != null;
        if (topDocs == null || topDocs.totalHits.value == 0 || topDocs.scoreDocs.length == 0) {
            return topDocs;
        }

        final Context rescoreCtx = (Context) rescoreContext;

        final var hits = topDocs.scoreDocs;
        final var windowSize = Math.min(rescoreCtx.getWindowSize(), hits.length);
        if (windowSize <= 0) {
            return topDocs;
        }

        if (rescoreCtx.shardSize > hits.length) {
            return topDocs;
        }

        return new TopDocs(topDocs.totalHits, Arrays.copyOfRange(hits, 0, rescoreCtx.shardSize));
    }

    @Override
    public Explanation explain(int topLevelDocId, IndexSearcher searcher, RescoreContext rescoreContext,
                               Explanation sourceExplanation) {
        // We cannot explain new scores because we only have single document at this point
        return sourceExplanation;
    }

    static class Context extends RescoreContext {
        private final int shardSize;

        Context(int windowSize, int shardSize) {
            super(windowSize, DummyGroupingMixupRescorer.INSTANCE);
            this.shardSize = shardSize;
        }
    }

    public static class Builder extends RescorerBuilder<Builder> {
        public static final String NAME = "_dummy_grouping_mixup";
        private static final ParseField SHARD_SIZE_FIELD = new ParseField("shard_size");

        private static final ObjectParser<Builder, Void> PARSER =
            new ObjectParser<>(NAME);
        static {
            PARSER.declareInt(Builder::shardSize, SHARD_SIZE_FIELD);
        }

        private int shardSize;

        public Builder() {
            super();
        }

        public Builder(StreamInput in) throws IOException {
            super(in);
            this.shardSize = in.readInt();
        }

        public Builder shardSize(int shardSize) {
            this.shardSize = shardSize;
            return this;
        }

        @Override
        public void doWriteTo(StreamOutput out) throws IOException {
            out.writeInt(shardSize);
        }

        @Override
        public void doXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject(NAME);
            builder.field(SHARD_SIZE_FIELD.getPreferredName(), shardSize);
            builder.endObject();
        }

        @Override
        public String getWriteableName() {
            return NAME;
        }

        @Override
        public RescorerBuilder<Builder> rewrite(QueryRewriteContext ctx) {
            return this;
        }

        @Override
        public RescoreContext innerBuildContext(int windowSize, QueryShardContext context) {
            return new Context(windowSize, shardSize);
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            Builder other = (Builder) obj;
            return shardSize == other.shardSize;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), shardSize);
        }

        public static Builder fromXContent(XContentParser parser)
            throws ParsingException
        {
            return PARSER.apply(parser, null);
        }
    }
}

package company.evo.elasticsearch;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchExtBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class GroupingMixupExtBuilder extends SearchExtBuilder {
    public static final String NAME = "grouping_mixup";

    private static final ParseField GROUP_FIELD_NAME = new ParseField("field");

    // Window size on which we will operate to group and rescore documents
    private static final ParseField WINDOW_SIZE_FIELD_NAME = new ParseField("window_size");
    private static final int DEFAULT_WINDOW_SIZE = 10_000;

    // Number of documents that will be returned from a shard
    private static final ParseField SHARD_SIZE_FIELD_NAME = new ParseField("shard_size");
    private static final int DEFAULT_SHARD_SIZE = 1_000;

    // Number of documents that will be returned from a shard
    private static final ParseField PAGINATION_FIELD_NAME = new ParseField("pagination");
    private static final boolean DEFAULT_PAGINATION = true;

    private static final ParseField RESCORE_SCRIPT_FIELD = new ParseField("rescore_script");

    private static final ConstructingObjectParser<GroupingMixupExtBuilder, Void> PARSER =
            new ConstructingObjectParser<>(
                    NAME,
                    args -> new GroupingMixupExtBuilder((String) args[0], (RescoreScript) args[1])
            );
    static {
        PARSER.declareString(ConstructingObjectParser.constructorArg(), GROUP_FIELD_NAME);
        PARSER.declareObject(ConstructingObjectParser.constructorArg(), RescoreScript.PARSER, RESCORE_SCRIPT_FIELD);
        PARSER.declareInt(GroupingMixupExtBuilder::windowSize, WINDOW_SIZE_FIELD_NAME);
        PARSER.declareInt(GroupingMixupExtBuilder::shardSize, SHARD_SIZE_FIELD_NAME);
        PARSER.declareBoolean(GroupingMixupExtBuilder::pagination, PAGINATION_FIELD_NAME);
    }

    private final String groupField;
    private final RescoreScript rescoreScript;
    private int windowSize = DEFAULT_WINDOW_SIZE;
    private int shardSize = DEFAULT_SHARD_SIZE;
    private boolean pagination = DEFAULT_PAGINATION;

    public static class RescoreScript implements Writeable {
        public final Script script;
        public final List<String> fields;

        private static final ParseField SCRIPT_FIELD = new ParseField("script");
        private static final ParseField FIELDS_FIELD = new ParseField("fields");
        @SuppressWarnings("unchecked")
        private static final ConstructingObjectParser<RescoreScript, Void> PARSER =
                new ConstructingObjectParser<>(
                        SCRIPT_FIELD.getPreferredName(),
                        args -> new RescoreScript((Script) args[0], (List<String>) args[1])
                );
        static {
            PARSER.declareObject(ConstructingObjectParser.constructorArg(), (p, c) -> Script.parse(p), SCRIPT_FIELD);
            PARSER.declareStringArray(
                    ConstructingObjectParser.optionalConstructorArg(),
                    FIELDS_FIELD
            );
        }

        public RescoreScript(Script script) {
            this(script, List.of());
        }

        public RescoreScript(Script script, List<String> fields) {
            this.script = script;
            this.fields = fields;
        }

        public RescoreScript(StreamInput in) throws IOException {
            script = new Script(in);
            fields = in.readOptionalStringList();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            script.writeTo(out);
            out.writeOptionalStringCollection(fields);
        }
    }

    public GroupingMixupExtBuilder(String groupField, RescoreScript rescoreScript) {
        this.groupField = groupField;
        this.rescoreScript = rescoreScript;
    }

    public GroupingMixupExtBuilder(StreamInput in) throws IOException {
        groupField = in.readString();
        windowSize = in.readInt();
        shardSize = in.readInt();
        pagination = in.readBoolean();
        rescoreScript = new RescoreScript(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(groupField);
        out.writeInt(windowSize);
        out.writeInt(shardSize);
        out.writeBoolean(pagination);
        rescoreScript.writeTo(out);
    }

    public static GroupingMixupExtBuilder fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    public String groupField() {
        return groupField;
    }

    public GroupingMixupExtBuilder windowSize(int size) {
        this.windowSize = size;
        return this;
    }

    public int windowSize() {
        return windowSize;
    }

    public GroupingMixupExtBuilder shardSize(int shardSize) {
        this.shardSize = shardSize;
        return this;
    }

    public int shardSize() {
        return shardSize;
    }

    public GroupingMixupExtBuilder pagination(boolean pagination) {
        this.pagination = pagination;
        return this;
    }

    public boolean pagination() {
        return pagination;
    }

    public RescoreScript rescoreScript() {
        return rescoreScript;
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.field(GROUP_FIELD_NAME.getPreferredName(), groupField);
        builder.field(WINDOW_SIZE_FIELD_NAME.getPreferredName(), windowSize);
        builder.field(SHARD_SIZE_FIELD_NAME.getPreferredName(), shardSize);
        builder.field(RESCORE_SCRIPT_FIELD.getPreferredName(), rescoreScript);
        builder.endObject();
        return builder;
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupField, windowSize, shardSize);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GroupingMixupExtBuilder)) {
            return false;
        }
        var other = (GroupingMixupExtBuilder) obj;
        return other.groupField.equals(groupField) &&
                other.windowSize == windowSize &&
                other.shardSize == shardSize &&
                other.rescoreScript.equals(rescoreScript);
    }
}

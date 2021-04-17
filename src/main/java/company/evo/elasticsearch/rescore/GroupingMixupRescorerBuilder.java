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

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.rescore.RescoreContext;
import org.elasticsearch.search.rescore.RescorerBuilder;

import java.io.IOException;
import java.util.Objects;

public class GroupingMixupRescorerBuilder extends RescorerBuilder<GroupingMixupRescorerBuilder> {
    public static final String NAME = "grouping_mixup";
    private static ParseField GROUPING_FIELD_FIELD = new ParseField("field", "group_field");
    private static ParseField RESCORE_SCRIPT_FIELD = new ParseField("rescore_script", "decline_script");

    private static final ConstructingObjectParser<GroupingMixupRescorerBuilder, Void> PARSER =
           new ConstructingObjectParser<>(
                   NAME,
                   args -> new GroupingMixupRescorerBuilder((String) args[0], (Script) args[1])
           );
    static {
        PARSER.declareString(ConstructingObjectParser.constructorArg(), GROUPING_FIELD_FIELD);
        PARSER.declareObject(ConstructingObjectParser.constructorArg(), (p, c) -> Script.parse(p), RESCORE_SCRIPT_FIELD);
    }

    private final String groupByField;
    private final Script rescoreScript;

    GroupingMixupRescorerBuilder(String groupByField, Script rescoreScript) {
        super();
        this.groupByField = groupByField;
        this.rescoreScript = rescoreScript;
    }

    public GroupingMixupRescorerBuilder(StreamInput in) throws IOException {
        super(in);
        this.groupByField = in.readString();
        this.rescoreScript = new Script(in);
    }

    @Override
    public void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(groupByField);
        rescoreScript.writeTo(out);
    }

    @Override
    public void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.field(GROUPING_FIELD_FIELD.getPreferredName(), groupByField);
        builder.field(RESCORE_SCRIPT_FIELD.getPreferredName(), rescoreScript);
        builder.endObject();
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public RescorerBuilder<GroupingMixupRescorerBuilder> rewrite(QueryRewriteContext ctx) {
        return this;
    }

    @Override
    public RescoreContext innerBuildContext(int windowSize, QueryShardContext context) {
        IndexFieldData<?> groupingField =
                this.groupByField == null ? null : context.getForField(context.fieldMapper(this.groupByField));
        ScoreScript.LeafFactory scriptFactory = context.compile(rescoreScript, ScoreScript.CONTEXT)
                .newFactory(rescoreScript.getParams(), context.lookup());
        return new GroupingMixupRescorer.Context(windowSize, groupingField, scriptFactory);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        GroupingMixupRescorerBuilder other = (GroupingMixupRescorerBuilder) obj;
        return groupByField.equals(other.groupByField)
                && rescoreScript.equals(other.rescoreScript);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), groupByField, rescoreScript);
    }

    public static GroupingMixupRescorerBuilder fromXContent(XContentParser parser)
            throws ParsingException
    {
        return PARSER.apply(parser, null);
    }
}

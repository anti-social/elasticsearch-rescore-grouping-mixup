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
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.rescore.RescoreContext;
import org.elasticsearch.search.rescore.RescorerBuilder;

import java.io.IOException;
import java.util.Objects;

public class GroupingMixupRescorerBuilder extends RescorerBuilder<GroupingMixupRescorerBuilder> {
    public static final ParseField NAME = new ParseField("grouping_mixup", "hit_group_position");
    private static ParseField GROUPING_FIELD_FIELD = new ParseField("field", "group_field");
    private static ParseField DECLINE_SCRIPT_FIELD = new ParseField("decline_script", "boost_script");

//    private static final ConstructingObjectParser<GroupingMixupRescorerBuilder, Void> PARSER =
//        new ConstructingObjectParser<GroupingMixupRescorerBuilder, Void>(NAME,
//                args -> {
//            return new GroupingMixupRescorerBuilder((String) args[0], (Script) args[1]);
//                });
//    static {
//        PARSER.declareString(constructorArg(), GROUPING_FIELD_FIELD);
//        PARSER.declareNamedObjects(constructorArg(), (p, c, n) -> Script.parse(p), DECLINE_SCRIPT_FIELD);
//        PARSER.declareNamedObjects(parseFromXContent());
//    }

    private final String groupingField;
    private final Script declineScript;

    GroupingMixupRescorerBuilder(String groupingField, Script declineScript) {
        super();
        this.groupingField = groupingField;
        this.declineScript = declineScript;
    }

    public GroupingMixupRescorerBuilder(StreamInput in) throws IOException {
        super(in);
        this.groupingField = in.readString();
        this.declineScript = new Script(in);
    }

    @Override
    public void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(groupingField);
        declineScript.writeTo(out);
    }

    @Override
    public void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME.getPreferredName());
        builder.field(GROUPING_FIELD_FIELD.getPreferredName(), groupingField);
        builder.field(DECLINE_SCRIPT_FIELD.getPreferredName(), declineScript);
        builder.endObject();
    }

    @Override
    public String getWriteableName() {
        return NAME.getPreferredName();
    }

    @Override
    public RescorerBuilder<GroupingMixupRescorerBuilder> rewrite(QueryRewriteContext ctx) {
        return this;
    }

    @Override
    public RescoreContext innerBuildContext(int windowSize, QueryShardContext context) {
        IndexFieldData<?> groupingField =
                this.groupingField == null ? null : context.getForField(context.fieldMapper(this.groupingField));
        SearchScript.LeafFactory scriptFactory = context.getScriptService()
                .compile(declineScript, SearchScript.CONTEXT)
                .newFactory(declineScript.getParams(), context.lookup());
        return new GroupingMixupRescorer.Context(windowSize, groupingField, scriptFactory);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        GroupingMixupRescorerBuilder other = (GroupingMixupRescorerBuilder) obj;
        return groupingField.equals(other.groupingField)
                && declineScript.equals(other.declineScript);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), groupingField, declineScript);
    }

    public static GroupingMixupRescorerBuilder fromXContent(XContentParser parser)
            throws IOException, ParsingException
    {
        String groupingField = null;
        Script script = null;
        String fieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                fieldName = parser.currentName();
            } else if (token == XContentParser.Token.START_OBJECT) {
                if (DECLINE_SCRIPT_FIELD.match(fieldName)) {
                    script = Script.parse(parser);
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                            "grouping_mixup rescorer doesn't support [" + fieldName + "]");
                }
            } else if (token.isValue()) {
                if (GROUPING_FIELD_FIELD.match(fieldName)) {
                    groupingField = parser.text();
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                            "grouping_mixup rescorer doesn't support [" + fieldName + "]");
                }
            }
        }

        if (groupingField == null) {
            throw new ParsingException(parser.getTokenLocation(),
                    "grouping_mixup rescorer requires [field] field");
        }

        return new GroupingMixupRescorerBuilder(groupingField, script);
    }
}

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

import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.test.AbstractWireSerializingTestCase;

import java.util.HashMap;
import java.util.Map;

public class GroupingMixupRescorerBuilderTests extends AbstractWireSerializingTestCase<GroupingMixupRescorerBuilder> {
    @Override
    protected GroupingMixupRescorerBuilder createTestInstance() {
        String groupingField = randomAlphaOfLength(5);
        Map<String, Object> scriptParams = new HashMap<>();
        return new GroupingMixupRescorerBuilder(
                groupingField,
                new Script(ScriptType.INLINE, "grouping_mixup_scripts", "position_recip", scriptParams)
        )
                .windowSize(between(0, Integer.MAX_VALUE));
    }

    @Override
    protected Writeable.Reader<GroupingMixupRescorerBuilder> instanceReader() {
        return GroupingMixupRescorerBuilder::new;
    }

    public void test() {
        // TODO Write real tests
    }
}

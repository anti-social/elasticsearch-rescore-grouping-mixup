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

package company.evo.elasticsearch.script;

import company.evo.elasticsearch.rescore.GroupingMixupRescorer;
import org.apache.lucene.index.LeafReaderContext;

import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.search.lookup.SearchLookup;
import org.elasticsearch.script.ScriptFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PositionRecipScript extends ScoreScript {

    private final double m;
    private final double a;
    private final double b;
    private final double c;

    private static final Map<String, Object> variables = new HashMap<>();

    private PositionRecipScript(double m, double a, double b, double c, SearchLookup lookup, LeafReaderContext leafContext) {
        super(Collections.emptyMap(), lookup, leafContext);
        this.m = m;
        this.a = a;
        this.b = b;
        this.c = c;
    }

    @Override
    public Map<String, Object> getParams() {
        return variables;
    }

    @Override
    public double execute(ExplanationHolder explanation) {
        return m / (a * (Double) variables.get(GroupingMixupRescorer.POSITION_PARAMETER_NAME) + b) + c;
    }

    public static class PositionRecipFactory implements ScoreScript.Factory, ScriptFactory {

        @Override
        public boolean isResultDeterministic() {
            return true;
        }

        @Override
        public LeafFactory newFactory(Map<String, Object> params, SearchLookup lookup) {
            double m = params.containsKey("m") ? (Double) params.get("m") : 1.0;
            double a = params.containsKey("a") ? (Double) params.get("a") : 1.0;
            double b = params.containsKey("b") ? (Double) params.get("b") : 1.0;
            double c = params.containsKey("c") ? (Double) params.get("c") : 0.0;

            return new LeafFactory() {
                @Override
                public ScoreScript newInstance(LeafReaderContext context) {
                    return new PositionRecipScript(m, a, b, c, lookup, context);
                }

                @Override
                public boolean needs_score() {
                    return false;
                }
            };
        }
    }
}

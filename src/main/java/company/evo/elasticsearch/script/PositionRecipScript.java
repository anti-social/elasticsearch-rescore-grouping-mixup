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

import org.apache.lucene.index.LeafReaderContext;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public class PositionRecipScript extends SearchScript {

    private final double m;
    private final double a;
    private final double b;
    private final double c;

    private double pos = 0.0;

    private PositionRecipScript(@Nullable Map<String, Object> params, SearchLookup lookup, LeafReaderContext leafContext) {
        super(params, lookup, leafContext);
        if (params == null) {
            params = Collections.emptyMap();
        }
        m = params.containsKey("m") ? (Double) params.get("m") : 1.0;
        a = params.containsKey("a") ? (Double) params.get("a") : 1.0;
        b = params.containsKey("b") ? (Double) params.get("b") : 1.0;
        c = params.containsKey("c") ? (Double) params.get("c") : 0.0;
    }

    @Override
    public void setNextVar(String name, Object value) {
        if (name.equals("_pos")) {
            pos = (Integer) value;
        } else {
            throw new IllegalArgumentException(
                    String.format(Locale.ENGLISH, "Only [_pos] variable is allowed but was: [%s]", name));
        }
    }

    @Override
    public double runAsDouble() {
        return m / (a * pos + b) + c;
    }

    public static class PositionRecipFactory implements Factory {
        @Override
        public LeafFactory newFactory(Map<String, Object> params, SearchLookup lookup) {
            return new LeafFactory() {
                @Override
                public SearchScript newInstance(LeafReaderContext context) {
                    return new PositionRecipScript(params, lookup, context);
                }

                @Override
                public boolean needs_score() {
                    return false;
                }
            };
        }
    }
}

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

package company.evo.elasticsearch.plugin;

import company.evo.elasticsearch.GroupingMixupExtBuilder;
import company.evo.elasticsearch.GroupingMixupFilter;
import company.evo.elasticsearch.rescore.DummyGroupingMixupRescorer;
import company.evo.elasticsearch.rescore.GroupingMixupRescorerBuilder;
import company.evo.elasticsearch.script.PositionRecipScriptEngine;
import company.evo.elasticsearch.script.RescoreScript;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.repositories.RepositoriesService;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.watcher.ResourceWatcherService;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class GroupingMixupPlugin extends Plugin
    implements ActionPlugin, SearchPlugin, ScriptPlugin
{
    private final Settings settings;
    private ScriptService scriptService;

    public GroupingMixupPlugin(Settings settings) {
        this.settings = settings;
    }

    @Override
    public Collection<Object> createComponents(
            Client client,
            ClusterService clusterService,
            ThreadPool threadPool,
            ResourceWatcherService resourceWatcherService,
            ScriptService scriptService,
            NamedXContentRegistry xContentRegistry,
            Environment environment,
            NodeEnvironment nodeEnvironment,
            NamedWriteableRegistry namedWriteableRegistry,
            IndexNameExpressionResolver indexNameExpressionResolver,
            Supplier<RepositoriesService> repositoriesServiceSupplier) {
        this.scriptService = scriptService;
        return Collections.emptyList();
    }

    @Override
    public List<RescorerSpec<?>> getRescorers() {
        return List.of(
            new RescorerSpec<>(
                GroupingMixupRescorerBuilder.NAME,
                GroupingMixupRescorerBuilder::new,
                GroupingMixupRescorerBuilder::fromXContent
            ),
            new RescorerSpec<>(
                DummyGroupingMixupRescorer.Builder.NAME,
                DummyGroupingMixupRescorer.Builder::new,
                DummyGroupingMixupRescorer.Builder::fromXContent
            )
        );
    }

    @Override
    public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
        return new PositionRecipScriptEngine();
    }

    @Override
    public List<ScriptContext<?>> getContexts() {
        return Collections.singletonList(RescoreScript.CONTEXT);
    }

    @Override
    public List<ActionFilter> getActionFilters() {
        return Collections.singletonList(
            new GroupingMixupFilter(settings, scriptService)
        );
    }

    @Override
    public List<SearchExtSpec<?>> getSearchExts() {
        return Collections.singletonList(
            new SearchExtSpec<>(
                GroupingMixupExtBuilder.NAME,
                GroupingMixupExtBuilder::new,
                GroupingMixupExtBuilder::fromXContent
            )
        );
    }
}

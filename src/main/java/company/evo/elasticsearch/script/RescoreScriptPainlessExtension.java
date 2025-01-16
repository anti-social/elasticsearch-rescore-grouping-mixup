package company.evo.elasticsearch.script;

import org.elasticsearch.painless.spi.PainlessExtension;
import org.elasticsearch.painless.spi.Whitelist;
import org.elasticsearch.painless.spi.WhitelistLoader;
import org.elasticsearch.script.ScriptContext;

import java.util.List;
import java.util.Map;

public class RescoreScriptPainlessExtension implements PainlessExtension {
    private static final Whitelist WHITELIST =
        WhitelistLoader.loadFromResourceFiles(RescoreScriptPainlessExtension.class, "rescore.script.whitelist.txt");

    @Override
    public Map<ScriptContext<?>, List<Whitelist>> getContextWhitelists() {
        return Map.of(RescoreScript.CONTEXT, List.of(WHITELIST));
    }
}

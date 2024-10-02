package company.evo.elasticsearch.script;

import org.elasticsearch.index.fielddata.ScriptDocValues;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptFactory;
import org.elasticsearch.search.SearchHit;

import java.util.List;
import java.util.Map;

public abstract class RescoreScript {
    public static final String[] PARAMETERS = {};

    public interface Factory extends ScriptFactory {
        RescoreScript newInstance(Map<String, Object> params);
    }

    public static final ScriptContext<Factory> CONTEXT = new ScriptContext<>("rescore", Factory.class);

    private final Map<String, Object> params;
    private final HitFieldsLookup hitLookup = new HitFieldsLookup();
    private int pos;

    public RescoreScript(Map<String, Object> params) {
        this.params = params;
    }

    /** Provides access to script parameters */
    public Map<String, Object> getParams() {
        return params;
    }

    public void setSearchHit(SearchHit hit, int pos) {
        hitLookup.setHit(hit);
        this.pos = pos;
    }

    /** Emulates doc['field'].value script API */
    public Map<String, ScriptDocValues<?>> getDoc() {
        return hitLookup;
    }

    /** Provides access to hit fields as a raw list of values: hit['field'][0] */
    public Map<String, List<?>> getHit() {
        // TODO: Implement non-copy API for accessing hit fields
        throw new UnsupportedOperationException();
    }

    /** Current hit score */
    public float get_score() {
        return hitLookup.hit().getScore();
    }

    /** Current hit position inside its group */
    public int get_pos() {
        return pos;
    }

    public abstract double execute();
}

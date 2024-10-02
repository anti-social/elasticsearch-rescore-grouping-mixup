package company.evo.elasticsearch.script;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.index.fielddata.ScriptDocValues;
import org.elasticsearch.search.SearchHit;

import java.io.IOException;
import java.util.*;

public class HitFieldsLookup implements Map<String, ScriptDocValues<?>> {
    private SearchHit hit = null;
    private final Map<String, Tuple<FakeDocValues, ScriptDocValues<?>>> fieldValuesCache = new HashMap<>();

    public SearchHit hit() {
        return hit;
    }

    public void setHit(SearchHit hit) {
        this.hit = hit;
        for (var cachedFieldValuesEntry : fieldValuesCache.entrySet()) {
            var fieldName = cachedFieldValuesEntry.getKey();
            var fieldValues = hit.field(fieldName).getValues();
            cachedFieldValuesEntry.getValue().v1().setValues(fieldValues);
            try {
                cachedFieldValuesEntry.getValue().v2().setNextDocId(0);
            } catch (IOException e) {
                throw ExceptionsHelper.convertToElastic(e);
            }
        }
    }

    @Override
    public ScriptDocValues<?> get(Object key) {
        final var fieldName = key.toString();
        final var values = hit.field(fieldName).getValues();

        ScriptDocValues<?> docValues;
        var cachedFieldValues = fieldValuesCache.get(fieldName);
        if (cachedFieldValues != null) {
            return cachedFieldValues.v2();
        } else if (!values.isEmpty()) {
            FakeDocValues fieldValues;
            final var first = values.get(0);
            if (first instanceof Long) {
                fieldValues = new FakeDocValues.Longs(values);
                docValues = new ScriptDocValues.Longs((FakeDocValues.Longs) fieldValues);
            } else if (first instanceof Boolean) {
                fieldValues = new FakeDocValues.Booleans(values);
                docValues = new ScriptDocValues.Booleans((FakeDocValues.Booleans) fieldValues);
            } else if (first instanceof Double) {
                fieldValues = new FakeDocValues.Doubles(values);
                docValues = new ScriptDocValues.Doubles((FakeDocValues.Doubles) fieldValues);
            } else if (first instanceof String) {
                fieldValues = new FakeDocValues.Strings(values);
                docValues = new ScriptDocValues.Strings((FakeDocValues.Strings) fieldValues);
            } else {
                throw new UnsupportedOperationException("Unsupported field type: " + first.getClass());
            }
            try {
                docValues.setNextDocId(0);
            } catch (IOException e) {
                throw ExceptionsHelper.convertToElastic(e);
            }
            fieldValuesCache.put(fieldName, new Tuple<>(fieldValues, docValues));
            return docValues;
        }

        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsKey(Object key) {
        var fieldName = key.toString();
        return hit.field(fieldName) != null;
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScriptDocValues<?> put(String key, ScriptDocValues<?> value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScriptDocValues<?> remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends String, ? extends ScriptDocValues<?>> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> keySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<ScriptDocValues<?>> values() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Map.Entry<String, ScriptDocValues<?>>> entrySet() {
        throw new UnsupportedOperationException();
    }
}

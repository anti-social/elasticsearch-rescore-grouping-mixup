package company.evo.elasticsearch.script;


import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.index.fielddata.SortedBinaryDocValues;
import org.elasticsearch.index.fielddata.SortedNumericDoubleValues;

import java.util.List;

public interface FakeDocValues {

    void setValues(List<Object> values);

    abstract class Numerics extends SortedNumericDocValues implements FakeDocValues {
        protected List<Object> values;
        protected int ix;

        public Numerics(List<Object> values) {
            this.values = values;
        }

        @Override
        public void setValues(List<Object> values) {
            this.values = values;
            this.ix = 0;
        }

        @Override
        public int docValueCount() {
            return values.size();
        }

        @Override
        public boolean advanceExact(int target) {
            return true;
        }

        @Override
        public int docID() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextDoc() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int advance(int target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long cost() {
            throw new UnsupportedOperationException();
        }
    }

    final class Longs extends Numerics implements FakeDocValues {
        public Longs(List<Object> values) {
            super(values);
        }

        @Override
        public long nextValue() {
            System.out.println("> nextValue: " + values + ", ix: " + ix);
            return (Long) values.get(ix++);
        }

    }

    final class Booleans extends Numerics implements FakeDocValues {
        public Booleans(List<Object> values) {
            super(values);
        }

        @Override
        public long nextValue() {
            return (Boolean) values.get(ix++) ? 1 : 0;
        }
    }

    final class Strings extends SortedBinaryDocValues implements FakeDocValues {
        private List<Object> values;
        private int ix;

        public Strings(List<Object> values) {
            this.values = values;
        }

        @Override
        public void setValues(List<Object> values) {
            this.values = values;
            this.ix = 0;
        }

        @Override
        public boolean advanceExact(int doc) {
            return true;
        }

        @Override
        public int docValueCount() {
            return values.size();
        }

        @Override
        public BytesRef nextValue() {
            return new BytesRef((String) values.get(ix++));
        }
    }

    final class Doubles extends SortedNumericDoubleValues implements FakeDocValues {
        private List<Object> values;
        private int ix;

        public Doubles(List<Object> values) {
            this.values = values;
        }

        @Override
        public void setValues(List<Object> values) {
            this.values = values;
            this.ix = 0;
        }

        @Override
        public double nextValue() {
            return (Double) values.get(ix++);
        }

        @Override
        public int docValueCount() {
            return values.size();
        }

        @Override
        public boolean advanceExact(int target) {
            return true;
        }
    }
}

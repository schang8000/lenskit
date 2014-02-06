/*
 * LensKit, an open source recommender systems toolkit.
 * Copyright 2010-2013 Regents of the University of Minnesota and contributors
 * Work on LensKit has been funded by the National Science Foundation under
 * grants IIS 05-34939, 08-08692, 08-12148, and 10-17697.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.grouplens.lenskit.vectors;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.primitives.Longs;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleCollection;
import it.unimi.dsi.fastutil.doubles.DoubleIterator;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.longs.*;
import org.apache.commons.lang3.StringUtils;
import org.grouplens.lenskit.collections.LongKeyDomain;
import org.grouplens.lenskit.symbols.Symbol;
import org.grouplens.lenskit.symbols.TypedSymbol;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;

/**
 * Read-only interface to sparse vectors.
 *
 * <p>
 * This vector class works a lot like a map, but it also caches some
 * commonly-used statistics. The values are stored in parallel arrays sorted by
 * key. This allows fast lookup and sorted iteration. All iterators access the
 * items in key order.
 *
 * <p>
 * Vectors have a <i>key domain</i>, which is a set containing all valid keys in
 * the vector. This key domain is fixed at construction; mutable vectors cannot
 * set values for keys not in this domain. Thinking of the vector as a function
 * from longs to doubles, the key domain would actually be the codomain, and the
 * key set the algebraic domain, but that gets cumbersome to write in code. So
 * think of the key domain as the domain from which valid keys are drawn.
 *
 * <p>
 * This class provides a <em>read-only</em> interface to sparse vectors. It may
 * actually be a {@link MutableSparseVector}, so the data may be modified by
 * code elsewhere that has access to the mutable representation. For sparse
 * vectors that are guaranteed to be unchanging, see
 * {@link ImmutableSparseVector}.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 * @compat Public
 */
public abstract class SparseVector implements Iterable<VectorEntry>, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The domain of keys.
     */
    final LongKeyDomain keys;
    /**
     * The value array. Indexes in this array correspond to indexes produced by {@link #keys}.
     */
    double[] values;

    //region Constructors
    /**
     * Construct a new vector from a key set and value array.
     * @param ks The key set.  Used as-is, and will be modified.  Pass a clone, usually.
     * @param vs The value array.
     */
    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    SparseVector(LongKeyDomain ks, double[] vs) {
        assert vs.length >= ks.domainSize();
        keys = ks;
        keys.acquire();
        values = vs;
    }

    /**
     * Construct a new sparse vector with a particular domain.  Allocates the value storage.
     * @param ks The key set. Used as-is, and will be modified. Pass a clone, usually.
     */
    SparseVector(LongKeyDomain ks) {
        this(ks, new double[ks.domainSize()]);
        ks.setAllActive(false);
    }

    /**
     * Construct a new vector from the contents of a map. The key domain is the
     * key set of the map.  Therefore, no new keys can be added to this vector.
     *
     * @param keyValueMap A map providing the values for the vector.
     */
    SparseVector(Long2DoubleMap keyValueMap) {
        keys = LongKeyDomain.fromCollection(keyValueMap.keySet(), true);
        final int len = keys.domainSize();
        values = new double[len];
        for (int i = 0; i < len; i++) {
            values[i] = keyValueMap.get(keys.getKey(i));
        }
    }
    //endregion

    //region Queries
    /**
     * Query whether the vector contains an entry for the key in question.
     *
     * @param key The key to search for.
     * @return {@code true} if the key exists.
     */
    public boolean containsKey(long key) {
        return keys.keyIsActive(key);
    }

    /**
     * Get the value for {@var key}.
     *
     * @param key the key to look up; the key must be in the key set.
     * @return the key's value
     * @throws IllegalArgumentException if {@var key} is not in the key set.
     */
    public double get(long key) {
        final int idx = keys.getIndexIfActive(key);
        if (idx >= 0) {
            return values[idx];
        } else {
            throw new IllegalArgumentException("Key " + key + " is not in the key set");
        }
    }

    /**
     * Get the value for {@var key}.
     *
     * @param key the key to look up
     * @param dft The value to return if the key is not in the vector
     * @return the value (or {@var dft} if the key is not set to a value)
     */
    public double get(long key, double dft) {
        final int idx = keys.getIndexIfActive(key);
        if (idx >= 0) {
            return values[idx];
        } else {
            return dft;
        }
    }

    /**
     * Get the value for the entry's key.
     *
     * @param entry A {@code VectorEntry} with the key to look up
     * @return the key's value
     * @throws IllegalArgumentException if the entry is unset, or if it is not from this vector or another vector
     * sharing the same key domain.  Only vectors and their side channels share key domains for the
     * purposes of this check.
     */
    public double get(VectorEntry entry) {
        final SparseVector evec = entry.getVector();
        final int eind = entry.getIndex();

        if (evec == null) {
            throw new IllegalArgumentException("entry is not associated with a vector");
        } else if (!evec.keys.isCompatibleWith(keys)) {
            throw new IllegalArgumentException("entry does not have safe key domain");
        }
        assert entry.getKey() == keys.getKey(eind);
        if (keys.indexIsActive(eind)) {
            return values[eind];
        } else {
            throw new IllegalArgumentException("Key " + entry.getKey() + " is not set");
        }
    }

    /**
     * Check whether an entry is set.
     * @param entry The entry.
     * @return {@code true} if the entry is set in this vector.
     * @throws IllegalArgumentException if the entry is not from this vector or another vector
     * sharing the same key domain.  Only vectors and their side channels share key domains for the
     * purposes of this check.
     */
    public boolean isSet(VectorEntry entry) {
        final SparseVector evec = entry.getVector();
        final int eind = entry.getIndex();

        if (evec == null) {
            throw new IllegalArgumentException("entry is not associated with a vector");
        } else if (!keys.isCompatibleWith(evec.keys)) {
            throw new IllegalArgumentException("entry does not have safe key domain");
        }
        assert entry.getKey() == keys.getKey(eind);
        return keys.indexIsActive(eind);
    }
    //endregion

    //region Iterators
    /**
     * Fast iterator over all set entries (it can reuse entry objects).
     *
     * @return a fast iterator over all key/value pairs
     * @see #fastIterator(VectorEntry.State)
     * @see it.unimi.dsi.fastutil.longs.Long2DoubleMap.FastEntrySet#fastIterator()
     *      Long2DoubleMap.FastEntrySet.fastIterator()
     */
    public Iterator<VectorEntry> fastIterator() {
        return fastIterator(VectorEntry.State.SET);
    }

    boolean isMutable() {
        return true;
    }

    /**
     * Fast iterator over entries (it can reuse entry objects).
     *
     * @param state The state of entries to iterate.
     * @return a fast iterator over all key/value pairs
     * @see it.unimi.dsi.fastutil.longs.Long2DoubleMap.FastEntrySet#fastIterator()
     *      Long2DoubleMap.FastEntrySet.fastIterator()
     * @since 0.11
     */
    public Iterator<VectorEntry> fastIterator(VectorEntry.State state) {
        IntIterator iter;
        switch (state) {
        case SET:
            iter = keys.activeIndexIterator(isMutable());
            break;
        case UNSET: {
            iter = keys.clone().invert().activeIndexIterator(false);
            break;
        }
        case EITHER: {
            iter = IntIterators.fromTo(0, keys.domainSize());
            break;
        }
        default: // should be impossible
            throw new IllegalArgumentException("invalid entry state");
        }
        return new FastIterImpl(iter, state);
    }

    /**
     * Return an iterable view of this vector using a fast iterator. This method
     * delegates to {@link #fast(VectorEntry.State)} with state {@link VectorEntry.State#SET}.
     *
     * @return This object wrapped in an iterable that returns a fast iterator.
     * @see #fastIterator()
     */
    public Iterable<VectorEntry> fast() {
        return fast(VectorEntry.State.SET);
    }

    /**
     * Return an iterable view of this vector using a fast iterator.
     *
     * @param state The entries the resulting iterable should return.
     * @return This object wrapped in an iterable that returns a fast iterator.
     * @see #fastIterator(VectorEntry.State)
     * @since 0.11
     */
    public Iterable<VectorEntry> fast(final VectorEntry.State state) {
        return new Iterable<VectorEntry>() {
            @Override
            public Iterator<VectorEntry> iterator() {
                return fastIterator(state);
            }
        };
    }

    // The default iterator for this SparseVector iterates over
    // entries that are "used".  It uses an IterImpl class that
    // generates a new VectorEntry for every element returned, so the
    // client can safely keep around the VectorEntrys without concern
    // they will mutate, at some cost in speed.
    @Override
    public Iterator<VectorEntry> iterator() {
        return new IterImpl();
    }

    private class IterImpl implements Iterator<VectorEntry> {
        private IntIterator iter = keys.activeIndexIterator(isMutable());

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        @Nonnull
        public VectorEntry next() {
            int pos = iter.nextInt();
            return new VectorEntry(SparseVector.this, pos,
                                   keys.getKey(pos), values[pos], true);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    // Given an int iterator, iterates over the elements of the <key,
    // value> pairs indexed by the int iterator.  For efficiency, may
    // reuse the VectorEntry returned at one step for a later step, so
    // the client should not keep around old VectorEntrys.
    private class FastIterImpl implements Iterator<VectorEntry> {
        private final VectorEntry.State state;
        private VectorEntry entry = new VectorEntry(SparseVector.this, -1, 0, 0, false);
        private IntIterator iter;

        public FastIterImpl(IntIterator positions, VectorEntry.State st) {
            iter = positions;
            state = st;
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        @Nonnull
        public VectorEntry next() {
            int pos = iter.nextInt();
            boolean isSet = state == VectorEntry.State.SET || keys.indexIsActive(pos);
            double v = isSet ? values[pos] : Double.NaN;
            entry.set(pos, keys.getKey(pos), v, isSet);
            return entry;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
    //endregion

    //region Domain, set, and value management
    /**
     * Get the key domain for this vector. All keys used are in this
     * set.  The keys will be in sorted order.
     *
     * @return The key domain for this vector.
     */
    public LongSortedSet keyDomain() {
        return keys.domain();
    }

    /**
     * Get the set of keys of this vector. It is a subset of the key
     * domain.  The keys will be in sorted order.
     *
     * @return The set of keys used in this vector.
     */
    public LongSortedSet keySet() {
        return keys.activeSetView();
    }

    /**
     * Get the set of unset keys.  This is \(D \\ S\), where \(D\) is the key domain and \(S\) the
     * key set.
     */
    public LongSortedSet unsetKeySet() {
        return keys.clone().invert().activeSetView();
    }

    /**
     * Return the keys of this vector sorted by value.
     *
     * @return A list of keys in nondecreasing order of value.
     * @see #keysByValue(boolean)
     */
    public LongArrayList keysByValue() {
        return keysByValue(false);
    }

    /**
     * Get the collection of values of this vector.
     *
     * @return The collection of all values in this vector.
     */
    public DoubleCollection values() {
        DoubleArrayList lst = new DoubleArrayList(size());
        IntIterator iter = keys.activeIndexIterator(false);
        while (iter.hasNext()) {
            int idx = iter.nextInt();
            lst.add(values[idx]);
        }
        return lst;
    }

    /**
     * Get the keys of this vector sorted by the value of the items
     * stored for each key.
     *
     * @param decreasing If {@code true}, sort in decreasing order.
     * @return The sorted list of keys of this vector.
     */
    public LongArrayList keysByValue(boolean decreasing) {
        long[] skeys = keySet().toLongArray();

        LongComparator cmp;
        // Set up the comparator. We use the key as a secondary comparison to get
        // a reproducible sort irrespective of sorting algorithm.
        if (decreasing) {
            cmp = new AbstractLongComparator() {
                @Override
                public int compare(long k1, long k2) {
                    int c = Double.compare(get(k2), get(k1));
                    if (c != 0) {
                        return c;
                    } else {
                        return Longs.compare(k1, k2);
                    }
                }
            };
        } else {
            cmp = new AbstractLongComparator() {
                @Override
                public int compare(long k1, long k2) {
                    int c = Double.compare(get(k1), get(k2));
                    if (c != 0) {
                        return c;
                    } else {
                        return Longs.compare(k1, k2);
                    }
                }
            };
        }

        LongArrays.quickSort(skeys, cmp);
        return LongArrayList.wrap(skeys);
    }

    /**
     * Get the size of this vector (the number of keys).
     *
     * @return The number of keys in the vector. This is at most the size of the
     *         key domain.
     */
    public int size() {
        return keys.size();
    }

    /**
     * Query whether this vector is empty.
     *
     * @return {@code true} if the vector is empty.
     */
    public boolean isEmpty() {
        return size() == 0;
    }
    //endregion

    //region Linear algebra
    /**
     * Compute and return the L2 norm (Euclidian length) of the vector.
     *
     * @return The L2 norm of the vector
     */
    public double norm() {
        double ssq = 0;
        DoubleIterator iter = values().iterator();
        while (iter.hasNext()) {
            double v = iter.nextDouble();
            ssq += v * v;
        }
        return Math.sqrt(ssq);
    }

    /**
     * Compute and return the L1 norm (sum) of the vector.
     *
     * @return the sum of the vector's values
     */
    public double sum() {
        double result = 0;
        if (keys.isCompletelySet()) {
            for (int i = keys.domainSize() - 1; i >= 0; i--) {
                result += values[i];
            }
        } else {
            DoubleIterator iter = values().iterator();
            while (iter.hasNext()) {
                result += iter.nextDouble();
            }
        }
        return result;
    }

    /**
     * Compute and return the mean of the vector's values.
     *
     * @return the mean of the vector
     */
    public double mean() {
        final int sz = size();
        return sz > 0 ? sum() / sz : 0;
    }

    /**
     * Compute the dot product between two vectors.
     *
     * @param o The other vector.
     * @return The dot (inner) product between this vector and {@var o}.
     */
    public double dot(SparseVector o) {
        if (keys.isCompletelySet() && o.keys.isCompletelySet()) {
            return fastDotProduct(o);
        } else {
            return slowDotProduct(o);
        }
    }

    private double fastDotProduct(SparseVector o) {
        double dot = 0;

        int sz1 = keys.domainSize();
        int sz2 = o.keys.domainSize();

        int i1 = 0, i2 = 0;
        while (i1 < sz1 && i2 < sz2) {
            final long k1 = keys.getKey(i1);
            final long k2 = o.keys.getKey(i2);
            if (k1 < k2) {
                i1++;
            } else if (k2 < k1) {
                i2++;
            } else {
                dot += values[i1] * o.values[i2];
                i1++;
                i2++;
            }
        }
        return dot;
    }

    private double slowDotProduct(SparseVector o) {
        // FIXME This code was tested, but no longer is.  Add relevant tests.
        double dot = 0;
        Iterator<VectorEntry> i1 = fastIterator();
        Iterator<VectorEntry> i2 = o.fastIterator();

        VectorEntry e1 = i1.hasNext() ? i1.next() : null;
        VectorEntry e2 = i2.hasNext() ? i2.next() : null;

        while (e1 != null && e2 != null) {
            final long k1 = e1.getKey();
            final long k2 = e2.getKey();
            if (k1 < k2) {
                e1 = i1.hasNext() ? i1.next() : null;
            } else if (k2 < k1) {
                e2 = i2.hasNext() ? i2.next() : null;
            } else {
                dot += e1.getValue() * e2.getValue();
                e1 = i1.hasNext() ? i1.next() : null;
                e2 = i2.hasNext() ? i2.next() : null;
            }
        }
        return dot;
    }

    /**
     * Count the common keys between two vectors.
     *
     * @param o The other vector.
     * @return The number of keys appearing in both this and the other vector.
     */
    public int countCommonKeys(SparseVector o) {
        int count = 0;
        Iterator<VectorEntry> i1 = fastIterator();
        Iterator<VectorEntry> i2 = o.fastIterator();

        VectorEntry e1 = i1.hasNext() ? i1.next() : null;
        VectorEntry e2 = i2.hasNext() ? i2.next() : null;

        while (e1 != null && e2 != null) {
            final long k1 = e1.getKey();
            final long k2 = e2.getKey();
            if (k1 < k2) {
                e1 = i1.hasNext() ? i1.next() : null;
            } else if (k2 < k1) {
                e2 = i2.hasNext() ? i2.next() : null;
            } else {
                count += 1;
                e1 = i1.hasNext() ? i1.next() : null;
                e2 = i2.hasNext() ? i2.next() : null;
            }
        }
        return count;
    }

    /**
     * Combine this vector with another vector by taking the union of the key domains of two vectors.
     * If both vectors have values the same key, the values in {@code o} override those from the
     * current vector.
     *
     * @param o The other vector
     * @return A vector whose key domain is the union of the key domains of this vector and the other.
     */
    public abstract SparseVector combineWith(SparseVector o);
    //endregion

    //region Object support
    @Override
    public String toString() {
        Function<VectorEntry, String> label = new Function<VectorEntry, String>() {
            @Override
            public String apply(VectorEntry e) {
                return String.format("%d: %.3f", e.getKey(), e.getValue());
            }
        };
        return "{" + StringUtils.join(Iterators.transform(fastIterator(), label), ", ") + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof SparseVector) {
            SparseVector vo = (SparseVector) o;

            int sz = size();
            int osz = vo.size();
            if (sz != osz) {
                return false;
            } else {
                if (!this.keySet().equals(vo.keySet())) {
                    return false;        // same keys
                }
                // we know that sparse vector values are always in key order. so just compare them.
                return this.values().equals(vo.values());
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return keySet().hashCode() ^ values().hashCode();
    }
    //endregion

    //region Copying
    /**
     * Return an immutable snapshot of this sparse vector. The new vector's key
     * domain may be shrunk to remove storage of unused keys; no keys in
     * the key set will be removed.
     *
     * @return An immutable sparse vector whose contents are the same as this
     *         vector. If the vector is already immutable, the returned object
     *         may be identical.
     */
    public abstract ImmutableSparseVector immutable();

    /**
     * Return a mutable copy of this sparse vector. The key domain of the
     * mutable vector will be the same as this vector's key domain.
     *
     * @return A mutable sparse vector which can be modified without modifying
     *         this vector.
     */
    public abstract MutableSparseVector mutableCopy();
    //endregion

    //region Channels
    /**
     * Return whether this sparse vector has a channel vector stored under a
     * particular symbol.
     *
     * @param channelSymbol the symbol under which the channel was
     *                      stored in the vector.
     * @return whether this vector has such a channel right now.
     */
    public abstract boolean hasChannelVector(Symbol channelSymbol);

    /**
     * Return whether this sparse vector has a channel stored under a
     * particular typed symbol.
     *
     * @param channelSymbol the typed symbol under which the channel was
     *                      stored in the vector.
     * @return whether this vector has such a channel right now.
     */
    public abstract boolean hasChannel(TypedSymbol<?> channelSymbol);

    /**
     * Get the vector associated with a particular unboxed channel.
     *
     * @param channelSymbol the symbol under which the channel was/is
     *                      stored in the vector.
     * @return The vector corresponding to the specified unboxed channel, or {@code null} if
     * there is no such channel.
     */
    public abstract SparseVector getChannelVector(Symbol channelSymbol);

    /**
     * Fetch the channel stored under a particular typed symbol.
     *
     * @param channelSymbol the typed symbol under which the channel was/is
     *                      stored in the vector.
     * @return the channel, which is itself a map from the key domain to objects of
     *                      the channel's type, or {@code null} if there is no such channel.
     */
    public abstract <K> Long2ObjectMap<K> getChannel(TypedSymbol<K> channelSymbol);

    /**
     * Retrieve all symbols that map to side channels for this vector.
     * @return A set of symbols, each of which identifies a side channel
     *         of the vector.
     */
    public abstract Set<Symbol> getChannelVectorSymbols();

    /**
     * Retrieve all symbols that map to typed side channels for this vector.
     * @return A set of symbols, each of which identifies a side channel
     *         of the vector.
     */
    public abstract Set<TypedSymbol<?>> getChannelSymbols();
    //endregion

    //region Static Constructors
    /**
     * Get an empty sparse vector.
     *
     * @return An empty sparse vector. The vector is immutable, because mutating an empty vector is
     *         impossible.
     */
    @SuppressWarnings("deprecation")
    public static ImmutableSparseVector empty() {
        return new ImmutableSparseVector();
    }
    //endregion
}

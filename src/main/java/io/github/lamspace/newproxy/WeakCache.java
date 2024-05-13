/*
 * Copyright 2024 the original author, Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lamspace.newproxy;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Cache mapping paris of {@code (key, sub-key) -> value}. Keys and values are weak, but sub-keys are strongly
 * referenced. Keys are passed directly to {@link #get(Object, Object)} method which also takes a {@code parameter}.
 * Sub-keys are calculated from keys and parameters using the {@code subKeyFactory} function passed to the constructor.
 * Values are calculated from keys and parameters using the {@code valueFactory} function passed to the constructor.
 * Keys can be {@code null} and are compared by identity while sub-keys returned by {@code subKeyFactory} or values
 * returned by {@code valueFactory} can not be null. Sub-keys are compared using their {@link #equals(Object)} method.
 * Entries are expunged from cache lazily on each invocation to {@link #get(Object, Object)},
 * {@link #containsValue(Object)} or {@link #size()} methods when the WeakReferences to keys are cleared. Cleared
 * WeakReferences to individual values don't cause expunging, but such entries are logically treated as non-existent
 * and trigger re-evaluation or {@code valueFactory} on request for their key/subKey.
 *
 * @param <K> type of keys
 * @param <P> type of parameters
 * @param <V> type of values
 * @author copied from {@link java.lang.reflect.WeakCache} cause that one is not available from outside packages.
 * @version 1.0.0
 * @see java.lang.reflect.WeakCache
 * @since 1.0.0
 */
final class WeakCache<K, P, V> {

    private final ReferenceQueue<K> refQueue = new ReferenceQueue<>();

    // the key type is Object for supporting null key
    private final ConcurrentMap<Object, ConcurrentMap<Object, Supplier<V>>> map = new ConcurrentHashMap<>();

    private final ConcurrentMap<Supplier<V>, Boolean> reverseMap = new ConcurrentHashMap<>();

    private final BiFunction<K, P, ?> subKeyFactory;

    private final BiFunction<K, P, V> valueFactory;

    /**
     * Construct an instance of {@code WeakCache}.
     *
     * @param subKeyFactory a function mapping a pair of {@code (key, parameter) -> sub-key}
     * @param valueFactory  a function mapping a pair of {@code (key, parameter) -> value}
     */
    public WeakCache(BiFunction<K, P, ?> subKeyFactory, BiFunction<K, P, V> valueFactory) {
        this.subKeyFactory = subKeyFactory;
        this.valueFactory = valueFactory;
    }

    /**
     * Look-up the value through the cache. This always evaluates the {@code subKeyFactory} function and
     * optionally evaluates {@code valueFactory} function if there is no entry in the cache for a given pair of
     * (key, subKey) or the entry has already been cleared.
     *
     * @param key       possibly null key
     * @param parameter parameter used together with a key to create sub-key and value (should not be null)
     * @return the cached value (never null)
     * @throws NullPointerException if {@code parameter} passed in or {@code sub-key} calculated by
     *                              {@code subKeyFactory} or {@code value} calculated by {@code valueFactory} is null.
     */
    public V get(K key, P parameter) {
        Objects.requireNonNull(parameter);
        expungeStaleEntries();
        Object cacheKey = CacheKey.valueOf(key, refQueue);

        // lazily install the 2nd level valuesMap for the particular cacheKey
        ConcurrentMap<Object, Supplier<V>> valuesMap = map.get(cacheKey);
        if (valuesMap == null) {
            ConcurrentMap<Object, Supplier<V>> oldValuesMap = map.putIfAbsent(cacheKey, valuesMap = new ConcurrentHashMap<>());
            if (oldValuesMap != null) {
                valuesMap = oldValuesMap;
            }
        }

        // create subKey and retrieve the possible Supplier<V> stored by that subKey from valuesMap
        Object subKey = Objects.requireNonNull(subKeyFactory.apply(key, parameter));
        Supplier<V> supplier = valuesMap.get(subKey);
        Factory factory = null;

        while (true) {
            if (supplier != null) {
                // supplier might be a Factory or a CacheValue(V) instance
                V value = supplier.get();
                if (value != null) {
                    return value;
                }
            }
            // else no supplier in cache
            // or a supplier that returned null (could be a cleared CacheValue
            // or a Factory that wasn't successfully in installing the CacheValue)

            // lazily construct a Factory
            if (factory == null) {
                factory = new Factory(key, parameter, subKey, valuesMap);
            }

            if (supplier == null) {
                supplier = valuesMap.putIfAbsent(subKey, factory);
                if (supplier == null) {
                    // successfully installed Factory
                    supplier = factory;
                }
                // else retry with winning supplier
            } else {
                if (valuesMap.replace(subKey, supplier, factory)) {
                    // successfully replaces
                    // cleared CacheEntry / unsuccessfully Factory
                    // with our Factory
                    supplier = factory;
                } else {
                    // retry with current supplier
                    supplier = valuesMap.get(subKey);
                }
            }
        }
    }

    /**
     * Checks whether the specified non-null value is already present in this {@code WeakCache}. The
     * check is made using identity comparison regardless of whether value's class overrides
     * {@link Object#equals(Object)} or not.
     *
     * @param value the non-null value to check
     * @return true if given {@code value} is already cached
     * @throws NullPointerException if value is null
     */
    public boolean containsValue(V value) {
        Objects.requireNonNull(value);
        expungeStaleEntries();
        return reverseMap.containsKey(new LookupValue<>(value));
    }

    /**
     * Returns the current number of cached entries that can decrease over tine when keys/values are GC-ed.
     *
     * @return current number of cached entries
     */
    public int size() {
        expungeStaleEntries();
        return reverseMap.size();
    }

    @SuppressWarnings(value = {"unchecked"})
    private void expungeStaleEntries() {
        WeakCache.CacheKey<K> cacheKey;
        while ((cacheKey = (WeakCache.CacheKey<K>) refQueue.poll()) != null) {
            cacheKey.expungeFrom(map, reverseMap);
        }
    }

    /**
     * Common type of value suppliers that are holding a referent.
     * The {@link #equals(Object)} and {@link #hashCode()} of implementation is defined
     * to compare the referent by identity.
     *
     * @param <V> generic parameter
     */
    private interface Value<V> extends Supplier<V> {
    }

    /**
     * An optimized {@link WeakCache.Value} used to look up the value in
     * {@link WeakCache#containsValue(Object)} method so that we are not constructing
     * the whole {@link CacheValue} just to look up the referent.
     *
     * @param <V> generic parameter
     */
    private static final class LookupValue<V> implements Value<V> {

        private final V value;

        LookupValue(V value) {
            this.value = value;
        }

        @Override
        public V get() {
            return this.value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this ||
                    obj instanceof Value && this.value == ((Value<?>) obj).get();
        }

    }

    /**
     * A {@link WeakCache.Value} that weakly references the referent.
     *
     * @param <V> generic parameter
     */
    private static final class CacheValue<V> extends WeakReference<V> implements Value<V> {

        private final int hash;

        CacheValue(V value) {
            super(value);
            this.hash = System.identityHashCode(value);
        }

        @Override
        public int hashCode() {
            return this.hash;

        }

        @Override
        public boolean equals(Object obj) {
            V value;
            return obj == this ||
                    obj instanceof Value && (value = get()) != null && value == ((Value<?>) obj).get();
        }

    }

    /**
     * CacheKey containing a weakly referenced {@code key}. It registers
     * itself with the {@code refQueue} so that it can be used to expunge
     * the entry when the {@link WeakReference} is cleared.
     *
     * @param <K> generic parameter
     */
    private static final class CacheKey<K> extends WeakReference<K> {

        private static final Object NULL_KEY = new Object();

        private final int hash;

        private CacheKey(K key, ReferenceQueue<K> refQueue) {
            super(key, refQueue);
            this.hash = System.identityHashCode(key);
        }

        static <K> Object valueOf(K key, ReferenceQueue<K> refQueue) {
            return key == null ? NULL_KEY : new CacheKey<>(key, refQueue);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object obj) {
            K key;
            return obj == this ||
                    obj != null && obj.getClass() == this.getClass() && (key = this.get()) != null && key == ((CacheKey<?>) obj).get();
        }

        void expungeFrom(ConcurrentMap<?, ? extends ConcurrentMap<?, ?>> map,
                         ConcurrentMap<?, Boolean> reverseMap) {
            ConcurrentMap<?, ?> valueMap = map.remove(this);
            if (valueMap != null) {
                for (Object cacheObject : valueMap.values()) {
                    reverseMap.remove(cacheObject);
                }
            }
        }

    }

    /**
     * A factory {@link Supplier} that implements the lazy synchronized construction of the value and
     * installment of it into the cache.
     */
    private final class Factory implements Supplier<V> {

        private final K key;

        private final P parameter;

        private final Object subKey;

        private final ConcurrentMap<Object, Supplier<V>> valuesMap;

        Factory(K key, P parameter, Object subKey, ConcurrentMap<Object, Supplier<V>> valuesMap) {
            this.key = key;
            this.parameter = parameter;
            this.subKey = subKey;
            this.valuesMap = valuesMap;
        }

        @Override
        public synchronized V get() {
            Supplier<V> supplier = valuesMap.get(subKey);
            if (supplier != this) {
                return null;
            }
            V value = null;
            try {
                value = Objects.requireNonNull(valueFactory.apply(key, parameter));
            } finally {
                if (value == null) {
                    valuesMap.remove(subKey, this);
                }
            }
            CacheValue<V> cacheValue = new CacheValue<>(value);
            reverseMap.put(cacheValue, Boolean.TRUE);
            if (!valuesMap.replace(subKey, this, cacheValue)) {
                throw new AssertionError("Should not reach here");
            }
            return value;
        }

    }

}

/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.Serialiser.*;
import plugins.Interdex.util.SkeletonMap.SkeletonSerialiser;

import java.util.TreeMap;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import java.util.AbstractSet;
import java.util.SortedMap;
import java.util.Collection;
import java.util.AbstractCollection;
import java.util.Iterator;

/**
** A {@link SkeletonMap} of a {@link TreeMap}.
**
** @author infinity0
*/
public class SkeletonTreeMap<K, V> extends TreeMap<K, V>
implements SkeletonMap<K, V> {

	/**
	** A TreeMap of objects tracking the load status of each key. This map
	** always contains the same keys as the main map, and for all keys k:
	** (loaded.get(k) != null) if and only if the value for k has not been
	** loaded into the map, and implies that (get(k) == null). (However, note
	** that (get(k) == null) could also be the actual loaded value for key k.)
	*/
	final TreeMap<K, Dummy<V>> loaded;

	public SkeletonTreeMap() {
		super();
		loaded = new TreeMap<K, Dummy<V>>();
	}

	public SkeletonTreeMap(Comparator<? super K> c) {
		super(c);
		loaded = new TreeMap<K, Dummy<V>>(c);
	}

	public SkeletonTreeMap(Map<? extends K,? extends V> m) {
		super(m);
		loaded = new TreeMap<K, Dummy<V>>();
		for (K key: m.keySet()) {
			loaded.put(key, Dummy.NULL);
		}
	}

	public SkeletonTreeMap(SortedMap<K,? extends V> m) {
		super(m);
		loaded = new TreeMap<K, Dummy<V>>(m.comparator());
		for (K key: m.keySet()) {
			loaded.put(key, Dummy.NULL);
		}
	}

	public SkeletonTreeMap(SkeletonTreeMap<K, V> m) {
		super(m);
		loaded = (TreeMap<K, Dummy<V>>)m.loaded.clone();
	}

	public SkeletonTreeMap(K[] keys) {
		loaded = new TreeMap<K, Dummy<V>>();
		for (K k: keys) {
			putDummy(k);
		}
	}

	public Dummy<V> putDummy(K key) {
		put(key, null);
		return loaded.put(key, Dummy.NULL);
	}

	public Dummy<V> putDummy(K key, Dummy<V> o) {
		put(key, null);
		return loaded.put(key, o);
	}

	protected Serialiser<V> serialiser;

	public void setSerialiser(Serialiser<V> s) {
		serialiser = s;
	}


	/************************************************************************
	 * public interface SkeletonMap
	 ************************************************************************/

	public boolean isFull() {
		// TODO use a counter to optimise this
		for (Dummy<V> o: loaded.values()) {
			if (o != null) { return false; }
		}
		return true;
	}

	public boolean isBare() {
		// TODO use a counter to optimise this
		for (Dummy<V> o: loaded.values()) {
			if (o == null) { return false; }
		}
		return true;
	}

	public Map<K, V> complete() {
		if (!isFull()) {
			throw new DataNotLoadedException("TreeMap not fully loaded.", this);
		} else {
			return new TreeMap(this);
		}
	}

	public void inflate() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public void deflate() {
		java.util.HashMap<K, PushTask<V>> tasks = new java.util.HashMap<K, PushTask<V>>();
		for (K k: keySet()) {
			if (loaded.get(k) != null) { continue; }
			V v = get(k);
			PushTask<V> d = serialiser.makePushTask(v);
			tasks.put(k, d);
		}
		serialiser.doPush(tasks.values());

		for (K k: tasks.keySet()) {
			putDummy(k, tasks.get(k).get());
		}
	}

	public void inflate(SkeletonMap<K, V> m) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public void deflate(SkeletonMap<K, V> m) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public void inflate(K key) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public void deflate(K key) {
		throw new UnsupportedOperationException("Not implemented.");
	}


	/**
	** A {@link Serialiser} that has access to all the fields of this class.
	*/
	abstract public static class TreeMapSerialiser<K, V> extends AbstractSerialiser<SkeletonTreeMap<K, V>>
	implements SkeletonSerialiser<SkeletonTreeMap<K, V>> {

		/**
		** Add all the data from a skeleton map to the given {@link PushTask}.
		** It is recommended that this method be called in the constructor of the
		** {@link PushTask} being passed in.
		**
		** @param de The task to receive the data.
		** @param skel The skeleton to add to the task.
		*/
		protected void putAll(PushTask<SkeletonTreeMap<K, V>> de, SkeletonTreeMap<K, V> map) {
			for (K k: map.loaded.keySet()) {
				de.put(k.toString(), map.loaded.get(k));
			}
		}

	}


	/************************************************************************
	 * public class TreeMap
	 ************************************************************************/

	public void clear() {
		super.clear();
		loaded.clear();
	}

	public Object clone() {
		return new SkeletonTreeMap(this);
	}

	public Comparator<? super K> comparator() { return super.comparator(); }

	public boolean containsKey(Object key) { return loaded.containsKey(key); }

	public boolean containsValue(Object value) {
		if (!isFull()) {
			throw new DataNotLoadedException("TreeMap not fully loaded.", this);
		} else {
			return super.containsValue(value);
		}
	}

	private Set<Map.Entry<K,V>> entries;
	public Set<Map.Entry<K,V>> entrySet() {
		if (entries == null) {
			entries = new AbstractSet<Map.Entry<K, V>>() {

				public int size() { return SkeletonTreeMap.this.size(); }

				public Iterator<Map.Entry<K, V>> iterator() {
					return new CombinedIterator(SkeletonTreeMap.super.entrySet().iterator(), SkeletonTreeMap.this.loaded.entrySet().iterator(), CombinedIterator.ENTRY);
				}

				public void clear() {
					SkeletonTreeMap.this.clear();
				}

				public boolean contains(Object o) {
					if (!(o instanceof Map.Entry)) { return false; }
					Map.Entry e = (Map.Entry)o;
					return SkeletonTreeMap.this.get(e.getKey()).equals(e.getValue());
				}

				public boolean remove(Object o) {
					boolean c = contains(o);
					if (c) {
						Map.Entry e = (Map.Entry)o;
						SkeletonTreeMap.this.remove(e.getKey());
					}
					return c;
				}

			};
		}
		return entries;
	}

	public K firstKey() { return loaded.firstKey(); }

	public V get(Object key) {
		Object o = loaded.get(key);
		if (o != null) {
			throw new DataNotLoadedException("Data not loaded for key " + key + ": " + o, this, key, o);
		}
		return super.get(key);
	}

	public SortedMap<K,V> headMap(K toKey) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	private Set<K> keys;
	public Set<K> keySet() {
		if (keys == null) {
			keys = new AbstractSet<K>() {

				public int size() { return SkeletonTreeMap.this.size(); }

				public Iterator<K> iterator() {
					return new CombinedIterator(SkeletonTreeMap.super.keySet().iterator(), SkeletonTreeMap.this.loaded.keySet().iterator(), CombinedIterator.KEY);
				}

				public void clear() { SkeletonTreeMap.this.clear(); }

				public boolean contains(Object o) {
					return SkeletonTreeMap.this.containsKey(o);
				}

				public boolean remove(Object o) {
					boolean c = contains(o);
					SkeletonTreeMap.this.remove(o);
					return c;
				}

			};
		}
		return keys;
	}

	public K lastKey() { return loaded.lastKey(); }

	/**
	** NOTE: if the value for the key hasn't been loaded yet, then this method
	** will return **null** instead of returning the actual previous value
	** (that hasn't been loaded yet).
	**
	** TODO: could code a setStrictChecksMode() or something to have this
	** method throw {@link DataNotLoadedException} in such circumstances, at
	** the user's discretion.
	*/
	public V put(K key, V value) {
		loaded.put(key, null);
		return super.put(key, value);
	}

	//public void putAll(Map<? extends K,? extends V> map);

	/**
	** NOTE: if the value for the key hasn't been loaded yet, then this method
	** will return **null** instead of returning the actual previous value
	** (that hasn't been loaded yet).
	**
	** TODO: could code a setStrictChecksMode() or something to have this
	** method throw {@link DataNotLoadedException} in such circumstances, at
	** the user's discretion.
	*/
	public V remove(Object key) {
		loaded.remove(key);
		return super.remove(key);
	}

	public int size() { return loaded.size(); }

	public SortedMap<K,V> subMap(K fromKey, K toKey) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public SortedMap<K,V> tailMap(K fromKey) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	private Collection<V> values;
	public Collection<V> values() {
		if (values == null) {
			values = new AbstractCollection<V>() {

				public int size() { return SkeletonTreeMap.this.size(); }

				public Iterator<V> iterator() {
					return new CombinedIterator(SkeletonTreeMap.super.values().iterator(), SkeletonTreeMap.this.loaded.values().iterator(), CombinedIterator.VALUE);
				}

				public void clear() { SkeletonTreeMap.this.clear(); }

			};
		}
		return values;
	}

	/************************************************************************
	 * public class AbstractMap
	 ************************************************************************/

	public boolean equals(Object o) {
		if (!(o instanceof SkeletonTreeMap)) { return false; }
		return super.equals(o) && loaded.equals(((SkeletonTreeMap)o).loaded);
	}
	public int hashCode() { return super.hashCode() ^ loaded.hashCode(); }
	public boolean isEmpty() { return loaded.isEmpty(); }
	// public String toString() { return super.toString(); }


	/**
	** Iterator that goes through both the loaded map and the object map at the
	** same time, throwing {@link DataNotLoadedException} when it encounters
	** dummy elements. After this occurs, all subsequent attempts to fetch the
	** next value will fail with {@link IllegalStateException} with the cause
	** set to the {@link DataNotLoadedException} that was thrown.
	**
	** @author infinity0
	*/
	private static class CombinedIterator<T> implements Iterator<T> {

		final private Iterator<T> iter;
		final private Iterator<?> iterloaded;

		final private int type;
		final private static int KEY = 0;
		final private static int VALUE = 1;
		final private static int ENTRY = 2;

		RuntimeException exceptionThrown;

		CombinedIterator(Iterator<T> it, Iterator<?> itl, int t) {
			iter = it;
			iterloaded = itl;
			type = t;
		}

		public boolean hasNext() {
			return iterloaded.hasNext();
		}

		public T next() throws DataNotLoadedException {

			if (exceptionThrown != null) {
				throw new IllegalStateException(exceptionThrown);
			}

			Object o = iterloaded.next();

			switch(type) {
			case ENTRY:
				Map.Entry e = (Map.Entry)o;
				if (e.getValue() != null) {
					exceptionThrown = new DataNotLoadedException("Data not loaded for key " + e.getKey() + ": " + e.getValue(), this, e.getKey(), e.getValue());
					throw exceptionThrown;
				}
				break;
			case VALUE:
				if (o != null) {
					exceptionThrown = new DataNotLoadedException("Data not loaded: " + o, this, null, o);
					throw exceptionThrown;
				}
				break;
			}

			return iter.next();
		}

		public void remove() {
			iterloaded.remove();
			iter.remove();
		}

	}

}

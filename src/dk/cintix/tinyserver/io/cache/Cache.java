package dk.cintix.tinyserver.io.cache;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 *
 * @author migo
 */
public class Cache<K, T> {

    private final long timeToLive;
    private final int maxItems;
    private final LinkedHashMap<K, CacheObject> cacheMap;

    public Cache(int maxItems) {
        timeToLive = -1;
        this.maxItems = maxItems;
        cacheMap = new LinkedHashMap<>(maxItems);
    }

    public Cache(long timeToLive) {
        if (timeToLive != -1) {
            this.timeToLive = timeToLive;
        } else {
            this.timeToLive = -1;
        }
        maxItems = 10000;
        cacheMap = new LinkedHashMap<>(maxItems);
    }

    public Cache(long timeToLive, int maxItems) {
        if (timeToLive != -1) {
            this.timeToLive = timeToLive;
        } else {
            this.timeToLive = -1;
        }

        this.maxItems = maxItems;
        cacheMap = new LinkedHashMap<>(maxItems);
    }

    /**
     * gets all items in the map
     *
     * @return
     */
    public List<T> getAll() {
        synchronized (cacheMap) {
            List<T> list = new ArrayList<>();
            for (CacheObject instance : cacheMap.values()) {
                list.add(instance.getObjecT());
            }
            return list;
        }
    }

    /**
     * Store InMemory Object in cache
     *
     * @param key
     * @param value
     */
    @SuppressWarnings("unchecked")
    public void put(K key, T value, CacheType type) {
        synchronized (cacheMap) {
            if (cacheMap.size() == maxItems) {
                K[] keys = (K[]) Array.newInstance(key.getClass(), maxItems);
                cacheMap.keySet().toArray(keys);
                cacheMap.remove(keys[0]);
            }
            cacheMap.put(key, new CacheObject(value, type));
            //for(CacheListener c: cacheListeners) c.update(key);

        }
    }

    /**
     *
     * Access InMemory Object from cache
     *
     * @param key
     * @return Generic value
     */
    @SuppressWarnings("unchecked")
    public T get(K key) {
        CacheObject c;
        synchronized (cacheMap) {
            c = (CacheObject) cacheMap.get(key);
        }

        cleanup();
        if (c == null) {
            return null;
        } else {
            return c.getObjecT();
        }

    }

    /**
     * Conatains key
     *
     * @param key
     * @return
     */
    public boolean contains(K key) {
        cleanup();
        return cacheMap.containsKey(key);
    }

    /**
     * Renew key in cache
     *
     * @param key
     */
    public void renew(K key) {
        synchronized (cacheMap) {
            CacheObject cacheObject = cacheMap.get(key);
            cacheObject.createdAt = System.currentTimeMillis();
            cacheMap.put(key, cacheObject);
        }
    }

    /**
     * Remove InMemory Object
     *
     * @param key
     */
    public void remove(K key) {
        synchronized (cacheMap) {
            cacheMap.remove(key);
        }
    }

    /**
     * Get items count from InMemory
     *
     * @return size
     */
    public int size() {
        synchronized (cacheMap) {
            return cacheMap.size();
        }
    }

    /**
     * Returns cachetime
     *
     * @param key
     * @return
     */
    public long getCacheTimeInSeconds(K key) {
        synchronized (cacheMap) {
            long time = System.currentTimeMillis() - cacheMap.get(key).createdAt;
            return time / 1000;
        }
    }

    /**
     * Remove all expired Objects from the InMemory Cache
     */
    @SuppressWarnings("unchecked")
    public void cleanup() {

        long now = System.currentTimeMillis();
        ArrayList<K> deleteKey;

        synchronized (cacheMap) {
            Iterator itr = cacheMap.keySet().iterator();

            deleteKey = new ArrayList<>((cacheMap.size() / 2) + 1);
            K key;
            CacheObject c;

            while (itr.hasNext()) {
                key = (K) itr.next();
                c = (CacheObject) cacheMap.get(key);
                if (c != null && c.type != CacheType.STATIC && timeToLive != -1 && (now > (timeToLive + c.createdAt))) {
                    deleteKey.add(key);
                }
            }
        }

        for (K key : deleteKey) {
            synchronized (cacheMap) {
                cacheMap.remove(key);
            }
        }
    }

    /**
     * Clear cache
     */
    public void clear() {
        synchronized (cacheMap) {
            cacheMap.clear();
        }
    }

    protected class CacheObject {

        private long createdAt = System.currentTimeMillis();
        private final T objecT;
        private final CacheType type;

        public CacheObject(T objecT, CacheType type) {
            this.objecT = objecT;
            this.type = type;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public T getObjecT() {
            return objecT;
        }

    }
}

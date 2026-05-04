package dk.cintix.application.server.io.cache;

import dk.cintix.application.server.TestSupport;
import java.util.List;

public class CacheTest {

    public void runAll() throws Exception {
        cache_storesAndReturnsValue();
        cache_expiresDynamicEntriesAfterTtl();
        cache_keepsStaticEntriesAfterTtl();
        cache_getAllReturnsStoredValues();
    }

    public void cache_storesAndReturnsValue() {
        // Arrange
        Cache<String, Integer> cache = new Cache<>(1000L, 10);

        // Act
        cache.put("a", 10, CacheType.DYNAMIC);
        Integer value = cache.get("a");

        // Assert
        TestSupport.assertEquals(Integer.valueOf(10), value, "Cache lookup failed");
        TestSupport.assertTrue(cache.contains("a"), "Cache should contain inserted key");
    }

    public void cache_expiresDynamicEntriesAfterTtl() throws Exception {
        // Arrange
        Cache<String, String> cache = new Cache<>(10L, 10);
        cache.put("ephemeral", "value", CacheType.DYNAMIC);

        // Act
        Thread.sleep(120L);
        cache.cleanup();
        String value = cache.get("ephemeral");

        // Assert
        TestSupport.assertNull(value, "Dynamic cache entry should expire");
        TestSupport.assertFalse(cache.contains("ephemeral"), "Expired key should be removed");
    }

    public void cache_keepsStaticEntriesAfterTtl() throws Exception {
        // Arrange
        Cache<String, String> cache = new Cache<>(10L, 10);
        cache.put("static", "value", CacheType.STATIC);

        // Act
        Thread.sleep(30L);
        String value = cache.get("static");

        // Assert
        TestSupport.assertEquals("value", value, "Static cache entry should not expire");
    }

    public void cache_getAllReturnsStoredValues() {
        // Arrange
        Cache<String, String> cache = new Cache<>(1000L, 10);
        cache.put("a", "A", CacheType.DYNAMIC);
        cache.put("b", "B", CacheType.DYNAMIC);

        // Act
        List<String> values = cache.getAll();

        // Assert
        TestSupport.assertEquals(2, values.size(), "Unexpected getAll size");
        TestSupport.assertTrue(values.contains("A"), "Missing value A");
        TestSupport.assertTrue(values.contains("B"), "Missing value B");
    }
}

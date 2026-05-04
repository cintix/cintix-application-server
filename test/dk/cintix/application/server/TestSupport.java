package dk.cintix.application.server;

public final class TestSupport {

    private TestSupport() {
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) {
            return;
        }
        if (expected != null && expected.equals(actual)) {
            return;
        }
        throw new AssertionError(message + " expected=<" + expected + "> actual=<" + actual + ">");
    }

    public static void assertArrayEquals(byte[] expected, byte[] actual, String message) {
        if (expected == null || actual == null || expected.length != actual.length) {
            throw new AssertionError(message);
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                throw new AssertionError(message);
            }
        }
    }

    public static void assertNull(Object value, String message) {
        if (value != null) {
            throw new AssertionError(message + " expected null but was " + value);
        }
    }
}

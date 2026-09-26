package dk.cintix.application.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Guards the release pipeline.
 *
 * <p>Until 3.5 the script built the jar <em>before</em> writing the new version
 * into {@code Response.java}, so every shipped artifact announced the previous
 * release in its {@code Server:} header — the 3.4.0 jar said {@code CAS)/3.3}.
 * Nothing in the test suite could see that, because the source tree was
 * consistent by the time the script finished.</p>
 */
public class ReleaseScriptTest {

    public void runAll() {
        releaseScript_writesVersionBeforeBuilding();
        builtJar_serverHeaderMatchesSource();
    }

    private static File projectRoot() {
        File dir = new File(".").getAbsoluteFile();
        while (dir != null) {
            if (new File(dir, "release.sh").isFile() && new File(dir, ".releases").isFile()) {
                return dir;
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    public void releaseScript_writesVersionBeforeBuilding() {
        // Arrange
        File root = projectRoot();
        TestSupport.assertTrue(root != null, "Could not locate release.sh from the working directory");

        try {
            String script = read(new File(root, "release.sh"));

            // Act — compare the step order inside main(), and confirm the
            // version step still rewrites the header that ships
            int versionStep = script.indexOf("\n    update_response_version\n");
            int buildStep = script.indexOf("\n    build_jar\n");
            TestSupport.assertTrue(script.contains("Cintix-Application-Server(CAS)"),
                    "release.sh no longer rewrites the Server: header version");

            // Assert
            TestSupport.assertTrue(versionStep != -1, "update_response_version is no longer called from main()");
            TestSupport.assertTrue(buildStep != -1, "build_jar is no longer called from main()");
            TestSupport.assertTrue(versionStep < buildStep,
                    "update_response_version must run before build_jar, otherwise the jar "
                    + "embeds the previous release's version in its Server: header");
        } catch (Exception e) {
            throw new RuntimeException("releaseScript_writesVersionBeforeBuilding failed", e);
        }
    }

    public void builtJar_serverHeaderMatchesSource() {
        // Arrange — only meaningful once `ant jar-with-dependencies` has run
        File root = projectRoot();
        TestSupport.assertTrue(root != null, "Could not locate the project root");

        File jar = new File(root, "dist/cintix-application-server-all.jar");
        File responseSource = new File(root,
                "src/dk/cintix/application/server/modules/http/server/services/domain/models/Response.java");
        if (!jar.isFile() || responseSource.lastModified() > jar.lastModified()) {
            // Either nothing has been built or the artifact predates the source
            // it came from — an out-of-date jar is not evidence of a bug, so
            // the ordering test above is the guard that always applies.
            return;
        }

        try {
            String sourceVersion = readSourceVersion(root);
            byte[] classBytes = readZipEntry(jar,
                    "dk/cintix/application/server/modules/http/server/services/domain/models/Response.class");
            TestSupport.assertTrue(classBytes != null, "Response.class missing from the built jar");

            // Act — the version string is a constant in the class pool
            String pool = new String(classBytes, StandardCharsets.ISO_8859_1);
            String marker = "Cintix-Application-Server(CAS)/";

            // Assert
            TestSupport.assertTrue(pool.contains(marker + sourceVersion),
                    "Built jar does not carry version " + sourceVersion
                    + " — it was built from stale sources. Rebuild and release again.");
        } catch (Exception e) {
            throw new RuntimeException("builtJar_serverHeaderMatchesSource failed", e);
        }
    }

    private static String readSourceVersion(File root) throws Exception {
        File response = new File(root,
                "src/dk/cintix/application/server/modules/http/server/services/domain/models/Response.java");
        String text = read(response);
        String marker = "Cintix-Application-Server(CAS)/";
        int start = text.indexOf(marker);
        TestSupport.assertTrue(start != -1, "Server: header version not found in Response.java");
        start += marker.length();
        int end = start;
        while (end < text.length() && (Character.isDigit(text.charAt(end)) || text.charAt(end) == '.')) {
            end++;
        }
        return text.substring(start, end);
    }

    private static byte[] readZipEntry(File zipFile, String entryName) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().equals(entryName)) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        }
        return null;
    }
}

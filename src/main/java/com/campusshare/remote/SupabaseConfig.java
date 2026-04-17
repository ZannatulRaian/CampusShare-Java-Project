package com.campusshare.remote;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Supabase configuration loader.
 *
 * Load priority (first match wins):
 *
 *   1. Environment variables  CAMPUSSHARE_SUPABASE_URL / CAMPUSSHARE_ANON_KEY
 *      → For CI/CD or system-level deployment.
 *
 *   2. campusshare.properties  next to the running JAR  (distribution file)
 *      → Admin places this alongside CampusShare.jar when distributing to
 *        teammates / teacher.  Teammates never see or touch the keys.
 *        File: <folder-containing-jar>/campusshare.properties
 *
 *   3. campusshare.properties  in ~/CampusShare/  (per-user config)
 *      → Set by Admin user via Profile → Cloud Connection inside the app.
 *        Only visible/editable by Admin role.
 *
 * Non-admin users never see credential fields.
 * The anon key is never displayed in plain text in the UI.
 */
public class SupabaseConfig {

    // ── Per-user config location ──────────────────────────────────────────────
    public static final String CONFIG_DIR  =
        System.getProperty("user.home") + File.separator + "CampusShare";
    public static final String CONFIG_FILE =
        CONFIG_DIR + File.separator + "campusshare.properties";

    // ── Live values ────────────────────────────────────────────────────────────
    public static String SUPABASE_URL  = "";
    public static String ANON_KEY      = "";
    public static String REST_URL      = "";
    public static String AUTH_URL      = "";
    public static String STORAGE_URL   = "";
    public static String REALTIME_URL  = "";

    /** True → skip Supabase even if configured (set by ConnectionMonitor). */
    public static volatile boolean FORCE_OFFLINE = false;

    /**
     * Where credentials were loaded from — used to show a friendly message
     * in the Admin UI without exposing the actual key value.
     */
    public static String configSource = "not configured";

    static { reload(); }

    public static synchronized void reload() {
        Properties props = loadProperties();
        SUPABASE_URL = props.getProperty("supabase.url",      "").trim();
        ANON_KEY     = props.getProperty("supabase.anon_key", "").trim();
        REST_URL     = SUPABASE_URL + "/rest/v1";
        AUTH_URL     = SUPABASE_URL + "/auth/v1";
        STORAGE_URL  = SUPABASE_URL + "/storage/v1";
        REALTIME_URL = SUPABASE_URL.replace("https://", "wss://") + "/realtime/v1/websocket";
    }

    public static boolean isConfigured() {
        return !SUPABASE_URL.isBlank()
            && !ANON_KEY.isBlank()
            && !SUPABASE_URL.contains("YOUR_PROJECT");
    }

    /** Masked key string for display — never show the real key in UI. */
    public static String maskedKey() {
        if (ANON_KEY.length() < 8) return "(not set)";
        return ANON_KEY.substring(0, 6) + "••••••••••••••••••••" +
               ANON_KEY.substring(ANON_KEY.length() - 4);
    }

    // ── Internal loader ────────────────────────────────────────────────────────

    private static Properties loadProperties() {
        Properties props = new Properties();

        // ── Priority 0: bundled credentials inside the JAR ────────────────────
        // These are baked in at build time by Maven resource filtering.
        // Admin runs `mvn clean package` with build.properties containing the keys,
        // then distributes the JAR — teammates need nothing else.
        try (InputStream bundled = SupabaseConfig.class
                .getResourceAsStream("/campusshare-bundled.properties")) {
            if (bundled != null) {
                Properties bp = new Properties();
                bp.load(bundled);
                String bUrl = bp.getProperty("supabase.url", "").trim();
                String bKey = bp.getProperty("supabase.anon_key", "").trim();
                // Only use if the placeholders were actually replaced by Maven
                if (!bUrl.isEmpty() && !bUrl.startsWith("${") && !bUrl.contains("YOUR_PROJECT")) {
                    props.setProperty("supabase.url",      bUrl);
                    props.setProperty("supabase.anon_key", bKey);
                    configSource = "bundled (pre-built JAR)";
                    System.out.println("[Config] Credentials loaded from bundled JAR.");
                    return props;
                }
            }
        } catch (Exception e) {
            System.err.println("[Config] Bundled properties error: " + e.getMessage());
        }

        // ── Priority 1: environment variables ─────────────────────────────────
        String envUrl = System.getenv("CAMPUSSHARE_SUPABASE_URL");
        String envKey = System.getenv("CAMPUSSHARE_ANON_KEY");
        if (envUrl != null && !envUrl.isBlank()) props.setProperty("supabase.url",      envUrl.trim());
        if (envKey != null && !envKey.isBlank()) props.setProperty("supabase.anon_key", envKey.trim());
        if (props.containsKey("supabase.url") && props.containsKey("supabase.anon_key")) {
            configSource = "environment variables";
            System.out.println("[Config] Credentials loaded from environment variables.");
            return props;
        }

        // ── Priority 2: config file next to the running JAR ───────────────────
        // This is the distribution method: admin ships JAR + campusshare.properties
        // together. Teammates just run the JAR — no setup, no keys visible.
        File jarSideConfig = findJarSideConfig();
        if (jarSideConfig != null && jarSideConfig.exists()) {
            try (InputStream in = new FileInputStream(jarSideConfig)) {
                props.load(in);
                String url = props.getProperty("supabase.url", "");
                if (!url.isBlank() && !url.contains("YOUR_PROJECT")) {
                    configSource = "bundled config (" + jarSideConfig.getName() + ")";
                    System.out.println("[Config] Credentials loaded from: " + jarSideConfig.getAbsolutePath());
                    return props;
                }
            } catch (IOException e) {
                System.err.println("[Config] Cannot read jar-side config: " + e.getMessage());
            }
        }

        // ── Priority 3: per-user config in ~/CampusShare/ ─────────────────────
        // Set by Admin via the in-app Cloud Connection settings.
        File userConfig = new File(CONFIG_FILE);
        if (userConfig.exists()) {
            try (InputStream in = new FileInputStream(userConfig)) {
                props.load(in);
                String url = props.getProperty("supabase.url", "");
                if (!url.isBlank() && !url.contains("YOUR_PROJECT")) {
                    configSource = "user config (~CampusShare)";
                    System.out.println("[Config] Credentials loaded from: " + CONFIG_FILE);
                } else {
                    configSource = "not configured";
                    System.out.println("[Config] Config file exists but has no valid credentials.");
                }
                return props;
            } catch (IOException e) {
                System.err.println("[Config] Cannot read " + CONFIG_FILE + ": " + e.getMessage());
            }
        } else {
            // Create a blank template so the user knows the file exists
            createTemplate(userConfig);
        }

        configSource = "not configured";
        return props;
    }

    /**
     * Finds campusshare.properties next to the running JAR file.
     * Returns null if the location cannot be determined.
     */
    private static File findJarSideConfig() {
        try {
            // Walk up from the class location to find the JAR's directory
            java.net.URL location = SupabaseConfig.class
                .getProtectionDomain().getCodeSource().getLocation();
            File jarFile = new File(location.toURI());
            File jarDir  = jarFile.isDirectory() ? jarFile : jarFile.getParentFile();
            if (jarDir != null) {
                return new File(jarDir, "campusshare.properties");
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Saves credentials to the per-user config (used by Admin in Profile tab). */
    public static void saveUserConfig(String url, String key) throws IOException {
        new File(CONFIG_DIR).mkdirs();
        String content =
            "# CampusShare — Supabase Configuration\n" +
            "# Managed by Admin via Profile → Cloud Connection.\n" +
            "supabase.url=" + url + "\n" +
            "supabase.anon_key=" + key + "\n";
        Files.writeString(Path.of(CONFIG_FILE), content);
        reload();
    }

    private static void createTemplate(File f) {
        try {
            new File(CONFIG_DIR).mkdirs();
            String template =
                "# CampusShare — Supabase Configuration\n" +
                "# This file is managed by the Admin user via the app.\n" +
                "# Do not edit manually unless you know what you are doing.\n" +
                "supabase.url=\n" +
                "supabase.anon_key=\n";
            Files.writeString(f.toPath(), template);
        } catch (IOException e) {
            System.err.println("[Config] Could not create template: " + e.getMessage());
        }
    }
}

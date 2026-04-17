package com.campusshare.remote;

/**
 * Holds the last-used login credentials in memory.
 * Used by ConnectionMonitor to re-authenticate when the connection is restored.
 * Never written to disk — cleared when the app exits.
 */
public class CredentialStore {
    public static volatile String email    = null;
    public static volatile String password = null;

    public static void save(String e, String p) { email = e; password = p; }
    public static void clear() { email = null; password = null; }
    public static boolean has() { return email != null && password != null; }
}

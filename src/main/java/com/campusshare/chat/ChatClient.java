package com.campusshare.chat;

/**
 * Chat client stub.
 * Real-time delivery uses LocalBroadcast for same-session users.
 * Cross-session persistence is via DAO / SQLite.
 */
public class ChatClient {
    public static void connect(String channel) {}
    public static void disconnect()            {}
}

package com.campusshare.remote;

import okhttp3.*;
import okio.ByteString;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Supabase Realtime client using Phoenix-protocol WebSocket.
 *
 * Subscribes to Postgres changes on specific tables.
 * When a change arrives (INSERT/UPDATE/DELETE), calls the registered listener.
 *
 * Usage:
 *   RealtimeClient rt = new RealtimeClient();
 *   rt.onTableChange("messages",  row -> SwingUtilities.invokeLater(() -> panel.onNewMessage(row)));
 *   rt.onTableChange("announcements", row -> DataStore.reloadAnnouncements());
 *   rt.connect();
 */
public class RealtimeClient {

    private static final OkHttpClient WS_CLIENT = new OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // no timeout for persistent WS
        .build();

    private WebSocket ws;
    private volatile boolean connected = false;
    private final Map<String, List<Consumer<JSONObject>>> listeners = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private int refCounter = 1;

    /** Register a listener for a table name. Listener is called on EDT background thread. */
    public void onTableChange(String table, Consumer<JSONObject> listener) {
        listeners.computeIfAbsent(table, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void connect() {
        if (connected) return;
        String url = SupabaseConfig.REALTIME_URL
            + "?apikey=" + SupabaseConfig.ANON_KEY
            + "&vsn=1.0.0";

        Request req = new Request.Builder().url(url).build();
        ws = WS_CLIENT.newWebSocket(req, new WebSocketListener() {

            @Override public void onOpen(WebSocket socket, Response response) {
                connected = true;
                System.out.println("[Realtime] Connected");
                // Subscribe to each table we have listeners for
                for (String table : listeners.keySet()) {
                    subscribeToTable(table);
                }
                // Phoenix heartbeat every 25 seconds
                heartbeat.scheduleAtFixedRate(() -> {
                    if (connected) {
                        JSONObject hb = new JSONObject()
                            .put("topic", "phoenix")
                            .put("event", "heartbeat")
                            .put("payload", new JSONObject())
                            .put("ref", String.valueOf(refCounter++));
                        socket.send(hb.toString());
                    }
                }, 25, 25, TimeUnit.SECONDS);
            }

            @Override public void onMessage(WebSocket socket, String text) {
                try {
                    JSONObject msg = new JSONObject(text);
                    String event = msg.optString("event");

                    if ("phx_reply".equals(event)) return; // subscription ack
                    if ("postgres_changes".equals(event)) {
                        JSONObject payload = msg.optJSONObject("payload");
                        if (payload == null) return;
                        JSONObject data = payload.optJSONObject("data");
                        if (data == null) return;
                        String table = data.optString("table");
                        JSONObject record = data.optJSONObject("record");
                        if (record == null) record = data.optJSONObject("old_record");
                        if (record == null) return;
                        final JSONObject finalRecord = record;
                        List<Consumer<JSONObject>> cbs = listeners.get(table);
                        if (cbs != null) {
                            for (Consumer<JSONObject> cb : cbs) {
                                try { cb.accept(finalRecord); }
                                catch (Exception e) { e.printStackTrace(); }
                            }
                        }
                    }
                } catch (Exception e) { /* ignore malformed frames */ }
            }

            @Override public void onFailure(WebSocket socket, Throwable t, Response r) {
                connected = false;
                System.err.println("[Realtime] Connection failed: " + t.getMessage());
                // Reconnect after 5 seconds
                heartbeat.schedule(() -> connect(), 5, TimeUnit.SECONDS);
            }

            @Override public void onClosed(WebSocket socket, int code, String reason) {
                connected = false;
                System.out.println("[Realtime] Disconnected: " + reason);
            }
        });
    }

    public void disconnect() {
        connected = false;
        heartbeat.shutdown();
        if (ws != null) ws.close(1000, "App closing");
    }

    private void subscribeToTable(String table) {
        String topic = "realtime:public:" + table;
        JSONObject join = new JSONObject()
            .put("topic", topic)
            .put("event", "phx_join")
            .put("payload", new JSONObject()
                .put("config", new JSONObject()
                    .put("broadcast", new JSONObject().put("self", true))
                    .put("postgres_changes", new org.json.JSONArray()
                        .put(new JSONObject()
                            .put("event", "*")
                            .put("schema", "public")
                            .put("table", table)))))
            .put("ref", String.valueOf(refCounter++));
        if (ws != null) ws.send(join.toString());
    }
}

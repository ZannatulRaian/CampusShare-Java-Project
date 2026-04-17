package com.campusshare.chat;

import java.util.*;
import java.util.function.Consumer;

/**
 * Simple in-process publish/subscribe for chat messages.
 * Listeners are called on the thread that publishes.
 * Panels subscribe when opened and unsubscribe on dispose.
 */
public class LocalBroadcast {

    private static final Map<String, List<Consumer<String>>> listeners = new HashMap<>();

    public static synchronized void subscribe(String channel, Consumer<String> listener) {
        listeners.computeIfAbsent(channel, k -> new ArrayList<>()).add(listener);
    }

    public static synchronized void unsubscribe(String channel, Consumer<String> listener) {
        List<Consumer<String>> list = listeners.get(channel);
        if (list != null) list.remove(listener);
    }

    public static synchronized void publish(String channel, String message) {
        List<Consumer<String>> list = listeners.getOrDefault(channel, Collections.emptyList());
        for (Consumer<String> l : list) {
            try { l.accept(message); } catch (Exception ignored) {}
        }
    }
}

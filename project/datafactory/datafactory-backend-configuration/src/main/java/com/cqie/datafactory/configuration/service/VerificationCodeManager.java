package com.cqie.datafactory.configuration.service;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VerificationCodeManager {

    private static final long TTL_SECONDS = 300;

    private final ConcurrentHashMap<String, CodeEntry> store = new ConcurrentHashMap<>();

    public String generate(String key) {
        String code = String.format("%06d", (int)(Math.random() * 1000000));
        store.put(key, new CodeEntry(code, Instant.now().plusSeconds(TTL_SECONDS)));
        return code;
    }

    public boolean verify(String key, String code) {
        CodeEntry entry = store.get(key);
        if (entry == null) return false;
        if (Instant.now().isAfter(entry.expiresAt)) {
            store.remove(key);
            return false;
        }
        boolean match = entry.code.equals(code);
        if (match) store.remove(key);
        return match;
    }

    public void remove(String key) { store.remove(key); }

    private record CodeEntry(String code, Instant expiresAt) {}
}

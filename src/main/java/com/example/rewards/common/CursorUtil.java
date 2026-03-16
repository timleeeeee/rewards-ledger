package com.example.rewards.common;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

public final class CursorUtil {

    private CursorUtil() {
    }

    public static String encode(OffsetDateTime createdAt, UUID id) {
        String raw = createdAt.toInstant().toEpochMilli() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid cursor format");
            }
            long epochMillis = Long.parseLong(parts[0]);
            return new Cursor(OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC), UUID.fromString(parts[1]));
        } catch (RuntimeException ex) {
            throw new BadRequestException("Invalid cursor");
        }
    }

    public record Cursor(OffsetDateTime createdAt, UUID id) {
    }
}
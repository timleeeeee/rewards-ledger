package com.example.rewards.unit;

import com.example.rewards.common.BadRequestException;
import com.example.rewards.common.CursorUtil;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CursorUtilTest {

    @Test
    void encodeDecodeRoundTripWorks() {
        OffsetDateTime now = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        UUID id = UUID.randomUUID();

        String cursor = CursorUtil.encode(now, id);
        CursorUtil.Cursor decoded = CursorUtil.decode(cursor);

        assertThat(decoded.createdAt()).isEqualTo(now);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void decodeInvalidCursorThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> CursorUtil.decode("not-a-valid-cursor"));
    }
}
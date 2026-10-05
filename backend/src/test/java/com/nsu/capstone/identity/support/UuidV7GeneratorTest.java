package com.nsu.capstone.identity.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    private final UuidV7Generator generator = new UuidV7Generator();

    @Test
    void generatesUuidVersion7() {
        UUID first = generator.generate();
        UUID second = generator.generate();

        assertEquals(7, first.version());
        assertEquals(2, first.variant());
        assertNotEquals(first, second);
    }
}

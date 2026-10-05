package com.nsu.capstone.identity.support;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.nsu.capstone.identity.application.UserIdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidV7Generator implements UserIdGenerator {

    private final NoArgGenerator generator = Generators.timeBasedEpochRandomGenerator();

    @Override
    public UUID generate() {
        return generator.generate();
    }
}

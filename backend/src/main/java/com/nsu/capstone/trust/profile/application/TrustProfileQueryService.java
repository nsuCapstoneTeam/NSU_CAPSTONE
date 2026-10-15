package com.nsu.capstone.trust.profile.application;

import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.trust.profile.domain.TrustProfile;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TrustProfileQueryService {

    private final TrustProfileAssembler assembler;

    public TrustProfileQueryService(TrustProfileAssembler assembler) {
        this.assembler = assembler;
    }

    public TrustProfile getTrustProfile(UUID callerId, UserRole callerRole, UUID artistId) {
        return assembler.assemble(artistId);
    }
}

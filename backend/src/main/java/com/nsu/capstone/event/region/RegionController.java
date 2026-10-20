package com.nsu.capstone.event.region;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "지역")
@RequestMapping("/api/v1/regions")
public class RegionController {

    private final RegionRepository regionRepository;

    public RegionController(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    @GetMapping
    public RegionListResponse list() {
        return RegionListResponse.from(regionRepository.findAllByOrderByCodeAsc());
    }
}

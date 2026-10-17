package com.nsu.capstone.event.region;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RegionListResponse(List<Province> provinces) {

    public record Province(String code, String name, List<District> districts) {
    }

    public record District(String code, String name) {
    }

    static RegionListResponse from(List<Region> regionsInCodeOrder) {
        Map<String, List<Region>> byProvince = new LinkedHashMap<>();
        for (Region region : regionsInCodeOrder) {
            byProvince.computeIfAbsent(region.getProvinceCode(), code -> new ArrayList<>())
                .add(region);
        }
        List<Province> provinces = byProvince.values().stream()
            .map(regions -> new Province(
                regions.getFirst().getProvinceCode(),
                regions.getFirst().getProvinceName(),
                regions.stream()
                    .map(region -> new District(region.getCode(), region.getName()))
                    .toList()
            ))
            .toList();
        return new RegionListResponse(provinces);
    }
}

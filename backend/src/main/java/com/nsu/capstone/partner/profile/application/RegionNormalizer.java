package com.nsu.capstone.partner.profile.application;

import com.nsu.capstone.event.region.Region;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 주요 활동 지역의 검증과 정규화(05 §2). 같은 지역이 한 가지 표현으로만 저장되게 한다.
 * 시·도 코드는 그 시·도 전체를 뜻한다.
 */
final class RegionNormalizer {

    private static final int PROVINCE_LENGTH = 2;

    private final Map<String, String> provinceOfCity = new HashMap<>();
    private final Map<String, Integer> citiesPerProvince = new HashMap<>();

    RegionNormalizer(List<Region> regions) {
        for (Region region : regions) {
            provinceOfCity.put(region.getCode(), region.getProvinceCode());
            citiesPerProvince.merge(region.getProvinceCode(), 1, Integer::sum);
        }
    }

    /** 목록에 없는 코드는 VALIDATION_ERROR다. 결과는 문자열 오름차순이다. */
    List<String> normalize(List<String> codes) {
        Set<String> provinces = new TreeSet<>();
        Set<String> cities = new TreeSet<>();
        for (String code : codes) {
            if (code != null && citiesPerProvince.containsKey(code) && code.length() == PROVINCE_LENGTH) {
                provinces.add(code);
            } else if (code != null && provinceOfCity.containsKey(code)) {
                cities.add(code);
            } else {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }
        Set<String> result = new TreeSet<>(provinces);
        for (String city : cities) {
            String province = provinceOfCity.get(city);
            if (citiesPerProvince.get(province) == 1) {
                result.add(province);
            } else if (!provinces.contains(province)) {
                result.add(city);
            }
        }
        return List.copyOf(result);
    }
}

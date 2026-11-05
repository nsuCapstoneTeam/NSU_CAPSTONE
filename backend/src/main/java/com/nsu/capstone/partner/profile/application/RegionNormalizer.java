package com.nsu.capstone.partner.profile.application;

import com.nsu.capstone.event.region.Region;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 주요 활동 지역의 검증과 정규화(05 §2). 같은 지역이 한 가지 표현으로만 저장되게 한다.
 * 시·도 코드는 그 시·도 전체를 뜻한다.
 */
final class RegionNormalizer {

    private static final Logger log = LoggerFactory.getLogger(RegionNormalizer.class);
    private static final int PROVINCE_LENGTH = 2;
    private static final Pattern CODE_FORMAT = Pattern.compile("[0-9]{2}|[0-9]{5}");

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
                // 형식은 맞는데 목록에 없으면 개편 마이그레이션의 이전 누락일 수 있어 남긴다(05 §5).
                // 형식이 틀린 값은 남기지 않는다(사용자 입력 오류이고, 줄바꿈 등으로 로그를 꾸밀 수 있다)
                if (code != null && CODE_FORMAT.matcher(code).matches()) {
                    log.warn("지역 목록에 없는 EVENT_PARTNER 프로필 지역 코드: {}", code);
                }
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

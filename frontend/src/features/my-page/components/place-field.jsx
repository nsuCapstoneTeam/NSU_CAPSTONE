import { useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import { PROVINCES } from '../../../data/regions.js';
import { districtsOf, regionCodeFromSigungu, regionLabel, searchAddresses } from '../event-place.js';
import PlaceMapPreview from './place-map-preview.jsx';

// 행사 장소 (NSU-115): 주소 검색 한 번으로 장소와 지역을 함께 정합니다.
// 1) 주소를 검색해 고르면 지역(시·군·구)이 자동으로 정해지고 위치 미리 보기가 나옴
// 2) 주소를 찾지 못하면 시·도 → 시·군·구를 직접 고름
// 3) 장소 이름은 직접 적음
// [체험판] 주소 검색은 예시 주소 목록에서 찾습니다. 실제 주소 검색 서비스는 팀 확정 필요
// form·update: 행사 폼 값과 바꾸는 함수, invalid(id): 저장을 눌렀는데 빠진 칸인지

function Req() {
  return (
    <span
      className="event-required"
      aria-hidden="true"
    >
      *
    </span>
  );
}

export default function PlaceField({ form, update, invalid }) {
  const [query, setQuery] = useState(form.address);
  // 검색 결과: null(아직 검색 안 함) | [] (없음) | [주소...]
  const [results, setResults] = useState(null);
  // 지역을 직접 고르는 중인지 (주소를 못 찾았거나, 주소의 지역을 못 찾았을 때)
  const [manual, setManual] = useState(Boolean(form.regionCode) && !form.address);
  const [notice, setNotice] = useState('');
  const [province, setProvince] = useState(form.regionCode.slice(0, 2));
  const [legacyName] = useState(form.regionCode ? '' : form.region);

  // 검색어를 고치면 고른 주소를 풀어서, 확인하지 않은 예전 주소가 저장되지 않게 함
  // 지역을 직접 고르는 중이면 적은 글을 주소로 두고, 고른 지역은 그대로 둠
  function changeQuery(value) {
    setQuery(value);
    setResults(null);
    if (manual) {
      update('address', value.trim());
    } else if (form.address && value !== form.address) {
      update('address', '');
      update('regionCode', '');
      setProvince('');
      setNotice('');
    }
  }

  function search() {
    const found = searchAddresses(query);
    setResults(found);
    setNotice(
      query.trim() && !found.length
        ? `'${query.trim()}'(으)로 찾은 주소가 없어요. 주소를 다시 확인하거나 아래에서 지역을 직접 골라 주세요.`
        : '',
    );
    // 찾지 못하면 이전에 고른 지역을 비우고 직접 고르게 함 (적은 글은 주소로 둠)
    if (query.trim() && !found.length) {
      setManual(true);
      setProvince('');
      update('regionCode', '');
      update('address', query.trim());
    }
  }

  function choose(address) {
    const code = regionCodeFromSigungu(address.sigunguCode);
    setQuery(address.road);
    setResults(null);
    update('address', address.road);
    if (code) {
      update('regionCode', code);
      setProvince(code.slice(0, 2));
      setManual(false);
      setNotice('');
    } else {
      // 주소 정보가 지역 목록보다 새로울 때 등
      update('regionCode', '');
      setManual(true);
      setNotice('이 주소의 지역을 찾지 못했어요. 아래에서 지역을 직접 골라 주세요.');
    }
    window.requestAnimationFrame(() => document.getElementById('event-venue')?.focus());
  }

  // 직접 고를 때는 검색 칸에 적은 글을 주소로 둠
  function pickProvince(code) {
    setProvince(code);
    update('regionCode', '');
    update('address', query.trim());
  }

  const label = regionLabel(form.regionCode);
  const chosen = Boolean(form.address) && Boolean(label) && !manual;
  // 예전에 '서울'처럼 지역 이름만 저장한 행사 (폼을 열 때 한 번만 판단, 지역을 고르면 숨김)
  const legacyRegion = !form.regionCode && legacyName;

  return (
    <div
      className="place-field"
      role="group"
      aria-labelledby="event-place-label"
    >
      <span
        id="event-place-label"
        className="place-field-label"
      >
        장소 <Req />
      </span>

      <div className="place-search">
        <span className="place-search-input">
          <Icon
            name="search"
            size={18}
          />
          <input
            id="event-regionCode"
            type="search"
            value={query}
            onChange={(e) => changeQuery(e.target.value)}
            onKeyDown={(e) => {
              // 엔터는 행사 저장이 아니라 주소 검색
              if (e.key === 'Enter') {
                e.preventDefault();
                search();
              }
            }}
            placeholder="도로명, 건물 이름, 지번으로 검색"
            aria-label="장소 주소 검색"
            aria-describedby="event-place-hint"
            aria-invalid={invalid('regionCode')}
            autoComplete="off"
          />
        </span>
        <button
          type="button"
          className="secondary"
          onClick={search}
        >
          주소 검색
        </button>
      </div>
      <small id="event-place-hint">
        체험판이라 예시 주소에서만 찾아요. 예: 한빛로 123, 연남로, 바다숲로, 애월
      </small>

      {legacyRegion && (
        <p className="place-note">
          예전에 &apos;{legacyRegion}&apos;(으)로 저장한 행사예요. 주소를 다시 골라 주세요.
        </p>
      )}

      {results?.length > 0 && (
        <ul
          className="place-results"
          aria-label={`주소 검색 결과 ${results.length}개`}
        >
          {results.map((a) => (
            <li key={a.road}>
              <button
                type="button"
                onClick={() => choose(a)}
              >
                <span>
                  <strong>{a.road}</strong>
                  <small>(지번) {a.jibun}</small>
                </span>
                <small>{a.zip}</small>
              </button>
            </li>
          ))}
        </ul>
      )}

      {notice && (
        <p
          className="place-warning"
          role="status"
        >
          {notice}
        </p>
      )}

      {manual && (
        <div className="form-pair">
          <label>
            <span>시·도</span>
            <select
              id="event-province"
              value={province}
              onChange={(e) => pickProvince(e.target.value)}
            >
              <option value="">시·도 선택</option>
              {PROVINCES.map((p) => (
                <option
                  key={p.code}
                  value={p.code}
                >
                  {p.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>시·군·구</span>
            <select
              id="event-district"
              value={form.regionCode}
              disabled={!province}
              onChange={(e) => update('regionCode', e.target.value)}
              aria-invalid={invalid('regionCode')}
            >
              <option value="">{province ? '시·군·구 선택' : '시·도를 먼저 골라 주세요'}</option>
              {districtsOf(province).map((d) => (
                <option
                  key={d.code}
                  value={d.code}
                >
                  {d.name}
                </option>
              ))}
            </select>
          </label>
        </div>
      )}

      {label && (
        <p className="place-region">
          <span className="place-region-chip">
            <Icon
              name="pin"
              size={14}
            />
            지역 <strong>{label}</strong>
          </span>
          <small>{manual ? '직접 고른 지역이에요.' : '주소를 고르면 지역이 자동으로 정해져요.'}</small>
        </p>
      )}

      {chosen && (
        <div className="place-preview">
          <PlaceMapPreview label={form.venue.trim() || form.address} />
          <small>지도는 예시 그림이라 실제 위치와 달라요. 위의 주소와 지역이 맞는지 확인해 주세요.</small>
        </div>
      )}

      <label>
        <span>
          장소 이름 <Req />
        </span>
        <input
          id="event-venue"
          required
          maxLength={80}
          value={form.venue}
          onChange={(e) => update('venue', e.target.value)}
          placeholder="예: 한빛대학교 대운동장"
          aria-invalid={invalid('venue')}
        />
      </label>
    </div>
  );
}

import { useEffect, useEffectEvent, useRef, useState } from 'react';
import Icon from '../../components/common/icon.jsx';
import PageHeading from '../../components/common/page-heading.jsx';
import EventDetail from './components/event-detail.jsx';
import EventList from './components/event-list.jsx';
import EventPlacePanel from './components/event-place-panel.jsx';
import { DEMO_PUBLIC_EVENTS } from './event-search-data.js';
import {
  countMap,
  dateParts,
  districtName,
  eventsIn,
  provinceName,
  publicEvents,
  venuePoint,
} from './event-search.js';
import RegionMap from './map/region-map.jsx';
import { PROVINCES } from '../../data/regions.js';
import './event-search.css';

// 행사 찾기 (NSU-116, 아티스트용): 지역 지도 → 행사 목록 → 장소 보기 → 행사 상세
// 지도(NSU-117): 전국 시·도 지도에서 시·도를 누르면 그 시·도의 시·군·구 지도와 목록으로 바뀌고,
// 시·군·구를 누르면 목록이 그 시·군·구 행사만 남습니다. Figma '행사 찾기 · 시안'의 '3D 지도' 화면.
// 시·도 지도에는 목록에 보이는 행사마다 번호 핀을 찍고, 목록 행과 핀이 함께 강조됩니다.
// [체험판] 행사는 예시 데이터입니다. 서버 연결 뒤에는 공개 행사 목록 API로 바꿉니다.
const EVENTS = publicEvents(DEMO_PUBLIC_EVENTS);
const NATION_COUNTS = countMap(EVENTS);
// 목록은 처음 8건, '더 보기'를 누를 때마다 8건씩 더 보여 줌
const PAGE = 8;
const SOURCE =
  '지도 경계: 통계청 SGIS 행정동 경계(공공누리 제1유형)를 vuski/admdongkor가 가공한 자료(CC BY 4.0)를 합쳐서 그렸어요.';

// 지도·목록 칸 위쪽이 화면 위로 지나갔으면 그 위치로 올림
function scrollToLayout() {
  const layout = document.querySelector('.es-stage');
  if (!layout) return;
  const top = layout.getBoundingClientRect().top + window.scrollY - 16;
  if (window.scrollY > top) window.scrollTo({ top, behavior: 'instant' });
}

// 시·군·구를 고른 뒤 목록이 짧아져 목록 제목이 화면 위로 사라졌으면 지도·목록 칸 위쪽으로 올림 (NSU-116 리뷰)
// 좁은 화면에서 제목이 지도 아래에 있을 때는 움직이지 않음 (지도의 핀과 강조로 결과를 바로 볼 수 있음)
function revealList() {
  const title = document.getElementById('es-list-title');
  if (title && title.getBoundingClientRect().top < 0) scrollToLayout();
}

const shortName = (code) => PROVINCES.find((p) => p.code === code)?.short ?? '';

export default function EventSearch({ notify }) {
  // 고른 시·도 코드 ('' = 전국 지도)
  const [province, setProvince] = useState('');
  // 고른 시·군·구 코드 ('' = 시·도 전체)
  const [district, setDistrict] = useState('');
  // 장소를 보고 있는 행사 id (없으면 목록)
  const [placeId, setPlaceId] = useState(null);
  // 상세를 보고 있는지 (placeId 행사의 상세)
  const [showDetail, setShowDetail] = useState(false);
  // 목록에 보이는 행사 수
  const [visible, setVisible] = useState(PAGE);
  // 목록 행이나 지도 핀에 마우스를 올린 행사 id (서로 함께 강조)
  const [hot, setHot] = useState('');
  // 화면이 바뀐 뒤 포커스를 옮길 요소 id (그린 뒤 한 번 옮기고 비움)
  const focusId = useRef(null);
  // 장소·상세로 가기 전 스크롤 위치 (돌아오면 그 자리로)
  const listScroll = useRef(0);
  const placeScroll = useRef(0);

  useEffect(() => {
    if (!focusId.current) return;
    document.getElementById(focusId.current)?.focus({ preventScroll: true });
    focusId.current = null;
  });
  const setFocusId = (id) => {
    focusId.current = id;
  };

  const list = eventsIn(EVENTS, province, district);
  const title = district
    ? `${shortName(province)} ${districtName(district)}`
    : province
      ? provinceName(province)
      : '전체 지역';
  const current = placeId ? EVENTS.find((e) => e.id === placeId) : null;
  // 시·도 지도의 번호 핀: 목록에 보이는 행사 중 좌표가 있는 행사에 차례로 번호
  // (좌표가 없는 행사는 번호 없이 목록에 '지도 위치 없음'으로 표시)
  const markers = province
    ? list
        .slice(0, visible)
        .filter((e) => venuePoint(e))
        .map((e, i) => {
          const { day, when } = dateParts(e);
          return {
            id: e.id,
            n: i + 1,
            at: venuePoint(e),
            code: String(e.regionCode),
            title: e.name,
            when: `${day} ${when}`,
            venue: e.venue,
          };
        })
    : [];
  const pinNumbers = province ? Object.fromEntries(markers.map((m) => [m.id, m.n])) : null;

  // 전국 지도·순위에서 시·도를 누름: 그 시·도 지도와 목록으로
  function openProvince(code) {
    setHot('');
    setProvince(code);
    setDistrict('');
    setPlaceId(null);
    setVisible(PAGE);
    setFocusId('es-list-title');
    requestAnimationFrame(scrollToLayout);
  }

  // 시·도 지도에서 시·군·구를 누름: 목록이 그 시·군·구 행사만. 한 번 더 누르면 풀림
  function pickDistrict(code) {
    setHot('');
    setDistrict(code === district ? '' : code);
    setPlaceId(null);
    setVisible(PAGE);
    requestAnimationFrame(revealList);
  }

  function backToNation() {
    setHot('');
    setProvince('');
    setDistrict('');
    setPlaceId(null);
    setVisible(PAGE);
    setFocusId('es-map-title');
  }

  // 목록 칩의 ✕와 '전체 보기': 시·군·구를 골랐으면 시·도 전체로, 아니면 전국으로
  function clearRegion() {
    if (district) {
      pickDistrict(district);
      setFocusId('es-list-title');
    } else backToNation();
  }

  // 더 보기: 새로 보인 첫 행으로 포커스
  function showMore() {
    setFocusId(`es-row-${list[visible]?.id}`);
    setVisible(visible + PAGE);
  }

  // 목록 행을 누르면 목록 자리에 장소. 목록 아래쪽에서 눌렀으면 장소 패널이 보이게 위로 올림
  function openPlace(id) {
    // 장소를 보는 중에 다른 핀을 누르면 목록 스크롤 자리는 그대로 둠
    if (!placeId) listScroll.current = window.scrollY;
    setHot('');
    setPlaceId(id);
    setFocusId('es-place-title');
    requestAnimationFrame(scrollToLayout);
  }

  // 목록으로 돌아가면 누른 행과 스크롤 위치를 그대로
  function closePlace() {
    setFocusId(`es-row-${placeId}`);
    setPlaceId(null);
    requestAnimationFrame(() => window.scrollTo({ top: listScroll.current, behavior: 'instant' }));
  }

  function openDetail() {
    placeScroll.current = window.scrollY;
    setShowDetail(true);
    window.scrollTo({ top: 0, behavior: 'instant' });
    setFocusId('es-detail-title');
  }

  function closeDetail() {
    setShowDetail(false);
    setFocusId('es-place-title');
    // 화면이 그려진 뒤 원래 스크롤 위치로
    requestAnimationFrame(() => window.scrollTo({ top: placeScroll.current, behavior: 'instant' }));
  }

  // Esc: 한 단계 위로 (장소 → 목록, 시·군·구 → 시·도 전체, 시·도 → 전국)
  const onEscape = useEffectEvent((e) => {
    if (e.key !== 'Escape' || showDetail || e.defaultPrevented) return;
    if (placeId) closePlace();
    else if (district) clearRegion();
    else if (province) backToNation();
  });
  useEffect(() => {
    window.addEventListener('keydown', onEscape);
    return () => window.removeEventListener('keydown', onEscape);
  }, []);

  if (current && showDetail) {
    return (
      <div className="event-search">
        <EventDetail
          event={current}
          onBack={closeDetail}
          onApply={() => notify('지원하기는 아직 연결 전이에요. 지원서에 넣을 내용과 API를 팀에서 정하고 있어요.')}
        />
      </div>
    );
  }

  const listArea = current ? (
    <EventPlacePanel
      event={current}
      backLabel={`${title} 행사 ${list.length}건`}
      onBack={closePlace}
      onOpenDetail={openDetail}
    />
  ) : (
    <EventList
      title={title}
      events={list.slice(0, visible)}
      total={list.length}
      onMore={showMore}
      regionSelected={Boolean(province)}
      clearLabel={district ? `${shortName(province)} 전체 보기` : '전체 지역 보기'}
      onClearRegion={clearRegion}
      onOpen={openPlace}
      pinNumbers={pinNumbers}
      hotId={hot}
      onHot={setHot}
    />
  );

  return (
    <div className="event-search">
      <PageHeading
        eyebrow="FIND YOUR STAGE"
        title="행사 찾기"
        description="공개된 행사를 지역별로 모아 보여 줘요. 지도에서 지역을 누르면 그 안으로 들어가고, 행사를 누르면 장소와 상세 내용을 볼 수 있어요."
      />

      {province ? (
        <>
          <div className="es-crumbs">
            <button
              type="button"
              className="secondary es-crumbs-back"
              onClick={backToNation}
            >
              <Icon
                name="chevron-left"
                size={16}
              />
              전국 보기
            </button>
            <nav aria-label="지금 보는 지역">
              <ol>
                <li>전국</li>
                <li aria-current={district ? undefined : 'page'}>{provinceName(province)}</li>
                {district && <li aria-current="page">{districtName(district)}</li>}
              </ol>
            </nav>
            <small>Esc로 한 단계 돌아가요</small>
          </div>
          <div className="es-stage es-zoom">
            <section
              className="panel es-map-card"
              aria-labelledby="es-map-title"
            >
              <div className="es-map-head">
                <h2
                  id="es-map-title"
                  tabIndex={-1}
                >
                  {provinceName(province)}
                </h2>
                <p>시·군·구를 누르면 그 지역 행사만, 번호 핀을 누르면 그 행사 장소를 보여요</p>
              </div>
              <RegionMap
                level={province}
                counts={countMap(EVENTS, province)}
                selected={district}
                onSelect={pickDistrict}
                markers={markers}
                hotMarker={hot}
                pickedMarker={placeId ?? ''}
                onMarkerEnter={setHot}
                onMarker={openPlace}
              />
              <p className="es-map-source">{SOURCE}</p>
            </section>
            {listArea}
          </div>
        </>
      ) : (
        <div className="es-stage es-zoom">
          <section
            className="panel es-map-card is-nation"
            aria-labelledby="es-map-title"
          >
            <div className="es-map-head">
              <h2
                id="es-map-title"
                tabIndex={-1}
              >
                지역별 행사
              </h2>
              <p>공개 행사 {EVENTS.length}건</p>
            </div>
            <RegionMap
              level="nation"
              counts={NATION_COUNTS}
              onSelect={openProvince}
            />
            <p className="es-map-source">{SOURCE}</p>
          </section>
          {listArea}
        </div>
      )}
      <p className="es-source">행사는 화면 확인용 예시예요. 행사 관계자가 공개로 등록한 행사가 여기에 보이게 될 거예요.</p>
    </div>
  );
}

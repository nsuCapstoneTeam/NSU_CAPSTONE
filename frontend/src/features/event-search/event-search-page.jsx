import { useEffect, useRef, useState } from 'react';
import PageHeading from '../../components/common/page-heading.jsx';
import EventDetail from './components/event-detail.jsx';
import EventList from './components/event-list.jsx';
import EventPlacePanel from './components/event-place-panel.jsx';
import RegionRank from './components/region-rank.jsx';
import { DEMO_PUBLIC_EVENTS } from './event-search-data.js';
import { eventsIn, provinceName, publicEvents, regionCounts } from './event-search.js';
import './event-search.css';

// 행사 찾기 (NSU-116, 아티스트용): 지역별 행사 수 → 행사 목록 → 장소 보기 → 행사 상세
// Figma '행사 찾기 · 시안'의 '행사 위치 보기 → 상세' 흐름. 3D 지도는 경계 자료가 정해지면 2차에서 붙입니다.
// [체험판] 행사는 예시 데이터입니다. 서버 연결 뒤에는 공개 행사 목록 API로 바꿉니다.
const EVENTS = publicEvents(DEMO_PUBLIC_EVENTS);
const COUNTS = regionCounts(EVENTS);
// 목록은 처음 8건, '더 보기'를 누를 때마다 8건씩 더 보여 줌
const PAGE = 8;

// 목록 위쪽(지역별 행사·목록 칸)이 화면 위로 지나갔으면 그 위치로 올림
function scrollToLayout() {
  const layout = document.querySelector('.es-layout');
  if (!layout) return;
  const top = layout.getBoundingClientRect().top + window.scrollY - 16;
  if (window.scrollY > top) window.scrollTo({ top, behavior: 'instant' });
}

export default function EventSearch({ notify }) {
  // 고른 시·도 코드 ('' = 전체 지역)
  const [province, setProvince] = useState('');
  // 장소를 보고 있는 행사 id (없으면 목록)
  const [placeId, setPlaceId] = useState(null);
  // 상세를 보고 있는지 (placeId 행사의 상세)
  const [showDetail, setShowDetail] = useState(false);
  // 목록에 보이는 행사 수
  const [visible, setVisible] = useState(PAGE);
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

  const list = eventsIn(EVENTS, province);
  const title = province ? provinceName(province) : '전체 지역';
  const current = placeId ? EVENTS.find((e) => e.id === placeId) : null;

  function selectRegion(code) {
    setProvince(code);
    setPlaceId(null);
    setVisible(PAGE);
    setFocusId('es-list-title');
  }

  // 더 보기: 새로 보인 첫 행으로 포커스
  function showMore() {
    setFocusId(`es-row-${list[visible]?.id}`);
    setVisible(visible + PAGE);
  }

  // 목록 행을 누르면 목록 자리에 장소. 목록 아래쪽에서 눌렀으면 장소 패널이 보이게 위로 올림
  function openPlace(id) {
    listScroll.current = window.scrollY;
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

  return (
    <div className="event-search">
      <PageHeading
        eyebrow="FIND YOUR STAGE"
        title="행사 찾기"
        description="공개된 행사를 지역별로 모아 보여 줘요. 지역을 고르고, 행사를 누르면 장소와 상세 내용을 볼 수 있어요."
      />
      <div className="es-layout">
        <RegionRank
          counts={COUNTS}
          total={EVENTS.length}
          selected={province}
          onSelect={selectRegion}
        />
        {current ? (
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
            onClearRegion={() => selectRegion('')}
            onOpen={openPlace}
          />
        )}
      </div>
      <p className="es-source">행사는 화면 확인용 예시예요. 행사 관계자가 공개로 등록한 행사가 여기에 보이게 될 거예요.</p>
    </div>
  );
}

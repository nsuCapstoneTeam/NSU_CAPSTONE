import { useRef, useState } from 'react';
import Icon from '../../components/common/icon.jsx';
import Avatar from './components/avatar.jsx';
import VerifiedBadge from './components/verified-badge.jsx';
import ProfileTab from './components/profile-tab.jsx';
import AvailabilityPanel from './components/availability-panel.jsx';
import WorksTab from './components/works-tab.jsx';
import BookingRequestsTab from './components/booking-requests-tab.jsx';
import SavedArtistsTab from './components/saved-artists-tab.jsx';
import EventsTab from './components/events-tab.jsx';
import './my-page.css';

// 역할별 사이드바 메뉴 [id, 메뉴 이름, 아이콘] (Figma '마이페이지 · 최종', NSU-110)
const TABS = {
  artist: [
    ['profile', '프로필', 'user'],
    ['schedule', '공연 일정', 'calendar'],
    ['works', '작업물', 'music'],
    ['requests', '섭외 요청', 'inbox'],
  ],
  organizer: [
    ['profile', '프로필', 'user'],
    ['events', '내 행사', 'ticket'],
    ['saved', '관심 아티스트', 'bookmark'],
  ],
};

// 메뉴별 본문 제목 아래 설명 (공연 일정은 보기 전환 버튼과 함께 일정 화면이 직접 그림)
const DESCRIPTIONS = {
  profile: {
    artist: '행사 관계자에게 보이는 기본 정보예요. 저장하면 이 브라우저에 보관돼요.',
    organizer: '행사 등록과 섭외 제안에 쓰는 기본 정보예요. 저장하면 이 브라우저에 보관돼요.',
  },
  works: '공연 영상과 음원을 등록하고 미리 들어 볼 수 있어요.',
  requests: '행사 관계자가 보낸 섭외 요청을 이곳에서 확인해요.',
  events: '등록한 행사를 보고, 행사마다 어울리는 아티스트를 추천받아요.',
  saved: '추천 결과에서 저장한 아티스트예요. 섭외 제안을 보내기 전에 다시 비교해 보세요.',
};

// 마이페이지: 왼쪽 사이드바에서 메뉴를 고르고, 오른쪽에 그 메뉴 내용 하나만 보여 줍니다.
// 사이드바는 접어서 아이콘만 남길 수 있습니다 (접으면 마우스를 올리거나 포커스할 때 메뉴 이름 표시).
// 메뉴 내용은 숨기기만 하고(hidden) 지우지 않아서, 메뉴를 바꿔도 쓰던 내용·재생 중인 파일이 유지됩니다.
export default function MyPage({
  profile,
  setProfile,
  schedules,
  setSchedules,
  events,
  setEvents,
  saved,
  toggleSaved,
  notify,
  initialTab = 'profile',
}) {
  const role = profile.role === 'organizer' ? 'organizer' : 'artist';
  const tabs = TABS[role];
  // 처음 열 메뉴 (예: 매칭 화면의 '행사 등록하러 가기' → 내 행사). 역할에 없는 메뉴면 프로필
  const [tab, setTab] = useState(() =>
    tabs.some(([id]) => id === initialTab) ? initialTab : 'profile',
  );
  // 사이드바 접힘 여부 (좁은 화면에서는 항상 위쪽 가로 메뉴로 보임)
  const [collapsed, setCollapsed] = useState(false);
  const tabRefs = useRef({});

  // 메뉴 목록에서 ↑/↓(←/→), Home/End로 이동 (화면낭독기 탭 패턴, 세로 방향)
  function tabKeyDown(e, index) {
    const last = tabs.length - 1;
    const prev = index === 0 ? last : index - 1;
    const next = index === last ? 0 : index + 1;
    const target = {
      ArrowDown: next,
      ArrowRight: next,
      ArrowUp: prev,
      ArrowLeft: prev,
      Home: 0,
      End: last,
    }[e.key];
    if (target === undefined) return;
    e.preventDefault();
    const id = tabs[target][0];
    setTab(id);
    tabRefs.current[id]?.focus();
  }

  // 메뉴 id → 내용
  const panels = {
    profile: (
      <ProfileTab
        profile={profile}
        setProfile={setProfile}
        notify={notify}
      />
    ),
    schedule: (
      <AvailabilityPanel
        active={tab === 'schedule'}
        schedules={schedules}
        setSchedules={setSchedules}
        notify={notify}
      />
    ),
    works: (
      <WorksTab
        active={tab === 'works'}
        profile={profile}
        setProfile={setProfile}
        notify={notify}
      />
    ),
    requests: <BookingRequestsTab />,
    events: (
      <EventsTab
        events={events}
        setEvents={setEvents}
        notify={notify}
      />
    ),
    saved: (
      <SavedArtistsTab
        saved={saved}
        toggleSaved={toggleSaved}
      />
    ),
  };

  const roleName = role === 'artist' ? '아티스트' : '행사 관계자';

  return (
    <div className={`my-page is-${role}${collapsed ? ' is-collapsed' : ''}`}>
      <aside
        className="my-sidebar"
        aria-label="마이페이지 메뉴"
      >
        {/* 이름·역할 (아티스트는 이름 옆에 확인 배지) */}
        <div className="my-sidebar-top">
          <span className="my-avatar-ring">
            <Avatar
              profile={profile}
              size={collapsed ? 30 : 42}
            />
          </span>
          <div className="my-sidebar-name">
            <p>
              <strong>{profile.name}</strong>
              {/* 본인 인증·검토가 아직 연결 전이라 확인된 항목이 없음 (회색) */}
              {role === 'artist' && <VerifiedBadge size={16} />}
            </p>
            <span className="my-role-chip">{roleName}</span>
          </div>
          <button
            type="button"
            className="my-sidebar-toggle"
            aria-label={collapsed ? '사이드바 펼치기' : '사이드바 접기'}
            aria-expanded={!collapsed}
            onClick={() => setCollapsed((v) => !v)}
          >
            <Icon
              name="sidebar"
              size={18}
            />
          </button>
        </div>

        {/* 메뉴: 선택된 메뉴만 Tab 키로 들어가고, 메뉴 사이는 방향키로 이동 */}
        <div
          className="my-nav"
          role="tablist"
          aria-orientation="vertical"
          aria-label={`${roleName} 마이페이지 메뉴`}
        >
          {tabs.map(([id, label, icon], index) => (
            <button
              key={id}
              ref={(el) => {
                tabRefs.current[id] = el;
              }}
              id={`my-tab-${id}`}
              type="button"
              role="tab"
              aria-selected={tab === id}
              aria-controls={`my-panel-${id}`}
              tabIndex={tab === id ? 0 : -1}
              onClick={() => setTab(id)}
              onKeyDown={(e) => tabKeyDown(e, index)}
            >
              <Icon
                name={icon}
                size={20}
              />
              <span className="my-nav-label">{label}</span>
            </button>
          ))}
        </div>

        <div className="my-sidebar-note">
          <Icon
            name="info"
            size={18}
          />
          <p>
            <strong>체험 프로필이에요</strong>
            <span>입력한 내용은 이 브라우저에만 저장돼요.</span>
          </p>
        </div>
      </aside>

      <div className="my-content">
        {tabs.map(([id, label]) => {
          const description = DESCRIPTIONS[id]?.[role] ?? DESCRIPTIONS[id];
          return (
            <div
              key={id}
              id={`my-panel-${id}`}
              role="tabpanel"
              aria-labelledby={`my-tab-${id}`}
              hidden={tab !== id}
            >
              {/* 공연 일정은 보기 전환 버튼이 있어 일정 화면이 제목을 직접 그림 */}
              {id !== 'schedule' && (
                <header className="my-content-head">
                  <div>
                    <h1>{label}</h1>
                    <p>{description}</p>
                  </div>
                </header>
              )}
              {panels[id]}
            </div>
          );
        })}
      </div>
    </div>
  );
}

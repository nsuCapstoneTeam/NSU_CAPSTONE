import { useRef, useState } from 'react';
import PageHeading from '../../components/common/page-heading.jsx';
import Tag from '../../components/common/tag.jsx';
import Avatar from './components/avatar.jsx';
import ProfileTab from './components/profile-tab.jsx';
import AvailabilityPanel from './components/availability-panel.jsx';
import WorksTab from './components/works-tab.jsx';
import SavedArtistsTab from './components/saved-artists-tab.jsx';
import './my-page.css';

// 역할별 탭 목록 [id, 탭 이름]
const TABS = {
  artist: [
    ['profile', '프로필'],
    ['schedule', '공연 일정'],
    ['works', '작업물·섭외'],
  ],
  organizer: [
    ['profile', '프로필'],
    ['saved', '관심 아티스트'],
  ],
};

// 마이페이지: 위쪽 이름 띠는 항상 보이고, 아래는 탭을 눌러 한 가지 내용만 봅니다.
// 탭 내용은 숨기기만 하고(hidden) 지우지 않아서, 탭을 바꿔도 쓰던 내용·재생 중인 파일이 유지됩니다.
export default function MyPage({
  profile,
  setProfile,
  schedules,
  setSchedules,
  saved,
  toggleSaved,
  notify,
}) {
  const tabs = TABS[profile.role] || TABS.artist;
  const [tab, setTab] = useState('profile');
  const tabRefs = useRef({});

  // 탭 목록에서 ←/→, Home/End로 탭 이동 (화면낭독기 탭 패턴)
  function tabKeyDown(e, index) {
    const last = tabs.length - 1;
    const next = {
      ArrowRight: index === last ? 0 : index + 1,
      ArrowLeft: index === 0 ? last : index - 1,
      Home: 0,
      End: last,
    }[e.key];
    if (next === undefined) return;
    e.preventDefault();
    const id = tabs[next][0];
    setTab(id);
    tabRefs.current[id]?.focus();
  }

  // 탭 id → 탭 내용
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
    saved: (
      <SavedArtistsTab
        saved={saved}
        toggleSaved={toggleSaved}
      />
    ),
  };

  return (
    <>
      <PageHeading
        eyebrow={profile.role === 'artist' ? 'ARTIST MY STAGE' : 'ORGANIZER DESK'}
        title={`${profile.name}님의 ${profile.role === 'artist' ? '아티스트' : '행사 관계자'} 마이페이지`}
        description={
          profile.role === 'artist'
            ? '프로필과 음원, 공연 가능 일정과 섭외 준비 상태를 관리하세요.'
            : '행사 정보와 관심 아티스트, 매칭 결과를 한곳에서 관리하세요.'
        }
      />
      {/* 이름·역할·검증 상태 요약 (모든 탭에서 보임) */}
      <div className="profile-strip">
        <Avatar
          profile={profile}
          size={56}
        />
        <div>
          <h2>{profile.name}</h2>
          <p>
            {profile.role === 'artist' ? '아티스트' : '행사 관계자'} · 이
            브라우저의 체험 프로필
          </p>
        </div>
        <Tag>{profile.verification}</Tag>
      </div>

      {/* 탭 버튼: 선택된 탭만 Tab 키로 들어가고, 탭 사이는 방향키로 이동 */}
      <div
        className="my-tabs"
        role="tablist"
        aria-label="마이페이지 메뉴"
      >
        {tabs.map(([id, label], index) => (
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
            {label}
          </button>
        ))}
      </div>

      {tabs.map(([id]) => (
        <div
          key={id}
          id={`my-panel-${id}`}
          role="tabpanel"
          aria-labelledby={`my-tab-${id}`}
          hidden={tab !== id}
        >
          {panels[id]}
        </div>
      ))}
    </>
  );
}

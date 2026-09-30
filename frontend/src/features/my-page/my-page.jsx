import { useState, useEffect, useRef } from 'react';
import { ARTISTS, GENRES, REGIONS } from '../matching/matching.js';
import Icon from '../../components/common/icon.jsx';
import PageHeading from '../../components/common/page-heading.jsx';
import Select from '../../components/common/select.jsx';
import Tag from '../../components/common/tag.jsx';

export default function MyPage({
  profile,
  setProfile,
  schedules,
  setSchedules,
  saved,
  toggleSaved,
  notify,
}) {
  const [draft, setDraft] = useState(profile);
  const [schedule, setSchedule] = useState({
    date: '2026-10-15',
    start: '18:00',
    end: '20:00',
  });
  const [audioFile, setAudioFile] = useState(null);
  const audioRef = useRef(null);
  useEffect(() => {
    if (!audioFile) return;
    const url = URL.createObjectURL(audioFile);
    if (audioRef.current) audioRef.current.src = url;
    return () => URL.revokeObjectURL(url);
  }, [audioFile]);
  const update = (key, value) =>
    setDraft((prev) => ({ ...prev, [key]: value }));
  function save(e) {
    e.preventDefault();
    if (!draft.name.trim()) {
      notify('이름을 입력해 주세요.');
      return;
    }
    setProfile({ ...draft, name: draft.name.trim() });
    notify('프로필을 이 브라우저에 저장했습니다.');
  }
  function addSchedule(e) {
    e.preventDefault();
    if (schedule.start >= schedule.end) {
      notify('종료 시간은 시작 시간보다 늦어야 합니다.');
      return;
    }
    if (
      schedules.some(
        (s) =>
          s.date === schedule.date &&
          s.start < schedule.end &&
          schedule.start < s.end,
      )
    ) {
      notify('같은 날짜에 겹치는 일정이 있습니다.');
      return;
    }
    setSchedules(
      [...schedules, { ...schedule, id: crypto.randomUUID() }].sort((a, b) =>
        (a.date + a.start).localeCompare(b.date + b.start),
      ),
    );
    notify('공연 가능 일정을 저장했습니다.');
  }
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
      <div className="profile-strip">
        <div className="user-avatar">
          <Icon
            name="user"
            size={30}
          />
        </div>
        <div>
          <h2>{profile.name}</h2>
          <p>
            {profile.role === 'artist' ? '아티스트' : '행사 관계자'} · 이
            브라우저의 체험 프로필
          </p>
        </div>
        <Tag>{profile.verification}</Tag>
      </div>
      <div className="my-grid">
        <div>
          <form
            className="panel stacked-form"
            onSubmit={save}
          >
            <h2>프로필</h2>
            <label>
              {profile.role === 'artist' ? '활동명' : '담당자 이름'}
              <input
                required
                maxLength={40}
                value={draft.name}
                onChange={(e) => update('name', e.target.value)}
              />
            </label>
            <div className="soft-box">
              가입 역할: <strong>{profile.role === 'artist' ? '아티스트' : '행사 관계자'}</strong>
              <br />역할 변경은 별도의 계정 전환 절차가 필요합니다.
            </div>
            <label>
              소개
              <textarea
                rows={3}
                maxLength={700}
                value={draft.bio}
                onChange={(e) => update('bio', e.target.value)}
                placeholder="나의 음악 또는 기획하는 행사를 소개해 주세요."
              />
            </label>
            {draft.role === 'artist' && (
              <>
                <div className="form-pair">
                  <Select
                    label="주요 장르"
                    value={draft.genre}
                    options={GENRES}
                    onChange={(v) => update('genre', v)}
                  />
                  <Select
                    label="활동 지역"
                    value={draft.region}
                    options={REGIONS}
                    onChange={(v) => update('region', v)}
                  />
                </div>
                <label>
                  기준 출연료 (만 원)
                  <input
                    type="number"
                    min="1"
                    max="100000"
                    required
                    value={draft.fee}
                    onChange={(e) => update('fee', Number(e.target.value))}
                  />
                </label>
                <label>
                  공식 채널 주소 (선택)
                  <input
                    type="url"
                    value={draft.channel}
                    onChange={(e) => update('channel', e.target.value)}
                    placeholder="https://…"
                  />
                </label>
                <small>
                  주소를 저장해도 채널 소유 인증이 완료되지는 않습니다.
                </small>
              </>
            )}
            {draft.role === 'organizer' && (
              <>
                <label>
                  소속 또는 단체명
                  <input
                    maxLength={60}
                    value={draft.organization || ''}
                    onChange={(e) => update('organization', e.target.value)}
                    placeholder="학교·기업·기관·단체명"
                  />
                </label>
                <div className="form-pair">
                  <Select
                    label="주요 행사 유형"
                    value={draft.eventType || '대학 축제'}
                    options={['대학 축제', '지역 축제', '기업 행사', '공연·콘서트', '결혼식', '기타']}
                    onChange={(v) => update('eventType', v)}
                  />
                  <Select
                    label="주요 행사 지역"
                    value={draft.region}
                    options={REGIONS}
                    onChange={(v) => update('region', v)}
                  />
                </div>
              </>
            )}
            <button
              className="primary"
              type="submit"
            >
              프로필 저장
            </button>
          </form>
          <section className="panel">
            <h2>검증 현황</h2>
            <p>
              실명·채널 인증과 관리자 검토가 연결되면 확인 결과가 표시됩니다.
            </p>
            <div className="verification-row">
              <span>본인확인</span>
              <Tag>미연결</Tag>
            </div>
            <div className="verification-row">
              <span>
                {profile.role === 'artist'
                  ? '외부 계정 OAuth'
                  : '주최 권한 확인'}
              </span>
              <Tag>미연결</Tag>
            </div>
            <div className="verification-row">
              <span>거래 신뢰도</span>
              <Tag>평가 이력 없음</Tag>
            </div>
            <h3>추가 검증 희망 항목</h3>
            {(profile.role === 'artist'
              ? [
                  ['business', '사업자등록정보'],
                  ['rights', '작업물 이용 권리'],
                  ['performance', '공식 발매·실연 참여·공연 활동'],
                ]
              : [
                  ['business', '사업자등록정보 또는 고유번호'],
                  ['eventProof', '행사 개최 증빙'],
                  ['authority', '소속·주최 권한 증빙'],
                ]
            ).map(([key, label]) => (
              <label
                key={key}
                className="checkbox"
              >
                <input
                  type="checkbox"
                  checked={Boolean(draft[key])}
                  onChange={(e) => update(key, e.target.checked)}
                />
                {label}
              </label>
            ))}
            <button
              className="secondary"
              onClick={() => {
                const next = {
                  ...profile,
                  business: draft.business,
                  rights: draft.rights,
                  performance: draft.performance,
                  verification: '검토 희망 저장',
                };
                setProfile(next);
                setDraft((prev) => ({
                  ...prev,
                  verification: next.verification,
                }));
                notify(
                  '검토 희망 항목만 저장했습니다. 인증 완료나 실제 검토 접수는 아닙니다.',
                );
              }}
            >
              검토 희망 항목 저장
            </button>
          </section>
        </div>
        <div>
          {profile.role === 'artist' ? (
            <>
              <section className="panel">
                <h2>공연 가능 일정</h2>
                <p>
                  저장한 일정은 내 체험 프로필에만 적용됩니다. 샘플 추천 후보의
                  일정과는 별개입니다.
                </p>
                <form
                  className="stacked-form"
                  onSubmit={addSchedule}
                >
                  <label>
                    날짜
                    <input
                      type="date"
                      required
                      value={schedule.date}
                      onChange={(e) =>
                        setSchedule({ ...schedule, date: e.target.value })
                      }
                    />
                  </label>
                  <div className="form-pair">
                    <label>
                      시작
                      <input
                        type="time"
                        required
                        value={schedule.start}
                        onChange={(e) =>
                          setSchedule({ ...schedule, start: e.target.value })
                        }
                      />
                    </label>
                    <label>
                      종료
                      <input
                        type="time"
                        required
                        value={schedule.end}
                        onChange={(e) =>
                          setSchedule({ ...schedule, end: e.target.value })
                        }
                      />
                    </label>
                  </div>
                  <button
                    className="secondary"
                    type="submit"
                  >
                    가능 일정 추가
                  </button>
                </form>
                <div className="schedule-list">
                  {schedules.map((s) => (
                    <div key={s.id}>
                      <p>
                        <strong>{s.date}</strong>
                        <br />
                        {s.start}–{s.end}
                      </p>
                      <button
                        className="text-button danger"
                        onClick={() =>
                          setSchedules(schedules.filter((x) => x.id !== s.id))
                        }
                        aria-label={`${s.date} ${s.start} 일정 삭제`}
                      >
                        삭제
                      </button>
                    </div>
                  ))}
                  {!schedules.length && (
                    <p className="muted">등록한 일정이 없습니다.</p>
                  )}
                </div>
              </section>
              <section className="panel">
                <h2>작업물 미리 듣기</h2>
                <p>
                  내 오디오를 선택하여 재생할 수 있습니다. 서버 업로드·분석·파일
                  보관은 아직 연결 전입니다.
                </p>
                <label className="upload-box">
                  <Icon
                    name="music"
                    size={26}
                  />
                  <span>음악 파일 선택 · 최대 20MB</span>
                  <input
                    type="file"
                    accept="audio/*,.mp3,.wav,.m4a,.ogg"
                    onChange={(e) => {
                      const f = e.target.files[0];
                      if (!f) return;
                      if (f.size > 20 * 1024 * 1024) {
                        notify('20MB 이하의 파일을 선택해 주세요.');
                        e.target.value = '';
                        return;
                      }
                      setAudioFile(f);
                    }}
                  />
                </label>
                {audioFile && (
                  <>
                    <p className="file-name">{audioFile.name}</p>
                    <audio
                      ref={audioRef}
                      controls
                      onError={() =>
                        notify('브라우저에서 재생할 수 없는 오디오 형식입니다.')
                      }
                    />
                    <small>이 화면을 나가면 선택한 파일은 해제됩니다.</small>
                  </>
                )}
              </section>
            </>
          ) : (
            <section className="panel">
              <h2>행사 관계자 안내</h2>
              <p>
                매칭 화면에서 행사 조건을 입력하고, 마음에 드는 아티스트를
                저장해 주세요. 자유게시판에서는 직접 모집 글을 작성할 수
                있습니다.
              </p>
              <div className="soft-box">
                사업자 정보만으로 행사 주최 권한이 확인되지는 않습니다. 실제
                검증 절차는 추후 연결합니다.
              </div>
            </section>
          )}
          {profile.role === 'artist' ? (
            <section className="panel">
              <h2>섭외 요청 우편함</h2>
              <p>새로운 섭외 요청이 도착하면 행사명, 일정, 지역과 요청 상태를 이곳에서 확인합니다.</p>
              <div className="soft-box">아직 도착한 체험 섭외 요청이 없습니다.</div>
            </section>
          ) : (
            <section className="panel">
              <h2>관심 아티스트 <em>{saved.length}</em></h2>
              {ARTISTS.filter((a) => saved.includes(a.id)).map((a) => (
                <div className="saved-row" key={a.id}>
                  <div>
                    <strong>{a.name}</strong>
                    <p>{a.genres.join(' · ')} · {a.fee}만 원</p>
                  </div>
                  <button className="text-button" onClick={() => toggleSaved(a.id)}>저장 해제</button>
                </div>
              ))}
              {!saved.length && <p>추천 카드의 북마크를 누르면 여기에 모아볼 수 있어요.</p>}
            </section>
          )}
        </div>
      </div>
    </>
  );
}

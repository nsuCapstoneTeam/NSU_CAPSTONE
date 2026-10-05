import { useState } from 'react';
import { GENRES, REGIONS } from '../../matching/matching.js';
import Select from '../../../components/common/select.jsx';
import Tag from '../../../components/common/tag.jsx';
import AvatarEditor from './avatar-editor.jsx';

// 마이페이지 '프로필' 탭: 프로필 수정 폼(왼쪽)과 검증 현황(오른쪽)
// 행사 관계자는 오른쪽에 이용 안내도 함께 보여 줍니다.
export default function ProfileTab({ profile, setProfile, notify }) {
  // draft: 저장 전 수정 중인 프로필 (저장 버튼을 눌러야 profile에 반영)
  const [draft, setDraft] = useState(profile);
  // 프로필 입력칸 하나의 값을 바꿈
  const update = (key, value) =>
    setDraft((prev) => ({ ...prev, [key]: value }));
  // 프로필 저장 (이름은 필수)
  function save(e) {
    e.preventDefault();
    if (!draft.name.trim()) {
      notify('이름을 입력해 주세요.');
      return;
    }
    // 사진·영상은 각자 바로 저장되므로 최신 값을 그대로 유지
    const saved = setProfile({
      ...draft,
      name: draft.name.trim(),
      avatar: profile.avatar,
      videos: profile.videos,
    });
    notify(
      saved
        ? '프로필을 이 브라우저에 저장했습니다.'
        : '프로필이 화면에는 반영됐지만 브라우저 저장에 실패했습니다. 새로고침하면 사라질 수 있습니다.',
    );
  }
  // 역할별 추가 검증 희망 항목 [저장 키, 표시 이름]
  // (행사 관계자 항목은 TRUST-142에 따라 정책 확정 전 임시 목록)
  const verificationItems =
    profile.role === 'artist'
      ? [
          ['business', '사업자등록정보'],
          ['rights', '작업물 이용 권리'],
          ['performance', '공식 발매·실연 참여·공연 활동'],
        ]
      : [
          ['business', '사업자등록정보 또는 고유번호'],
          ['eventProof', '행사 개최 증빙'],
          ['authority', '소속·주최 권한 증빙'],
        ];
  return (
    <div className="my-grid">
      <div>
        {/* 프로필 사진 꾸미기 */}
        <AvatarEditor
          profile={profile}
          onSave={(avatar) => setProfile({ ...profile, avatar })}
          notify={notify}
        />
        {/* 프로필 수정 폼 */}
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
          {/* 아티스트 전용 항목 */}
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
          {/* 행사 관계자 전용 항목 */}
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
      </div>
      <div>
        {/* 검증 현황: 실제 인증은 미연결, 검토 희망 항목만 저장 */}
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
          {/* 역할별 추가 검증 희망 항목 [저장 키, 표시 이름] */}
          {verificationItems.map(([key, label]) => (
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
              // 화면에 보이는 역할별 항목을 그대로 저장 (행사 관계자 항목이 빠지던 문제 수정)
              const next = {
                ...profile,
                ...Object.fromEntries(
                  verificationItems.map(([key]) => [key, Boolean(draft[key])]),
                ),
                verification: '검토 희망 저장',
              };
              const saved = setProfile(next);
              setDraft((prev) => ({
                ...prev,
                verification: next.verification,
              }));
              notify(
                saved
                  ? '검토 희망 항목만 저장했습니다. 인증 완료나 실제 검토 접수는 아닙니다.'
                  : '검토 희망 항목이 화면에는 반영됐지만 브라우저 저장에 실패했습니다.',
              );
            }}
          >
            검토 희망 항목 저장
          </button>
        </section>
        {profile.role === 'organizer' && (
          // 행사 관계자: 이용 안내
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
      </div>
    </div>
  );
}

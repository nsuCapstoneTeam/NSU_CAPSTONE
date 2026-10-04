import { useState, useRef } from 'react';
import {
  ARTISTS,
  DEFAULT_EVENT,
  GENRES,
  MOODS,
  REGIONS,
  TYPES,
  TEMPOS,
  RHYTHMS,
  matchArtists,
  validateEvent,
} from './matching.js';
import Icon from '../../components/common/icon.jsx';
import PageHeading from '../../components/common/page-heading.jsx';
import Select from '../../components/common/select.jsx';
import Tag from '../../components/common/tag.jsx';
import ArtistResult from './components/artist-result.jsx';

// 행사 관계자용 매칭 화면: 왼쪽 행사 조건 입력, 오른쪽 추천 결과
// search: 마지막으로 검색한 조건 (App이 보관하므로 다른 화면에 다녀와도 결과 유지)
export default function Matching({ saved, toggleSaved, search, setSearch }) {
  // form: 입력 중인 조건 (검색 버튼을 눌러야 search에 반영)
  const [form, setForm] = useState(search || DEFAULT_EVENT);
  const [error, setError] = useState('');
  const resultsRef = useRef(null);
  // 저장된 검색 조건이 있을 때만 추천 계산
  const report = search ? matchArtists(search) : null;
  // 입력칸 하나의 값을 바꿈
  const change = (name, value) =>
    setForm((prev) => ({ ...prev, [name]: value }));
  // 검색: 입력 확인 → 오류 없으면 조건 저장 후 결과 영역으로 포커스 이동
  function submit(e) {
    e.preventDefault();
    const message = validateEvent(form);
    setError(message);
    if (message) return;
    setSearch({ ...form });
    setTimeout(() => resultsRef.current?.focus(), 0);
  }
  return (
    <>
      <PageHeading
        eyebrow="FIND YOUR MATCH"
        title="원하는 스타일을 알려주세요!"
        description="필수 조건으로 걸러내고, 항목별 근거로 비교하세요."
      />
      <div className="matching-layout">
        {/* 행사 조건 입력 폼 */}
        <aside>
          <form
            className="panel match-form"
            onSubmit={submit}
          >
            <div className="form-heading">
              <h2>행사 조건</h2>
              <button
                type="button"
                className="text-button"
                onClick={() => {
                  setForm({ ...DEFAULT_EVENT });
                  setError('');
                }}
              >
                예시 채우기
              </button>
            </div>
            <label>
              원하는 음악과 분위기
              <textarea
                rows={3}
                maxLength={700}
                value={form.description}
                onChange={(e) => change('description', e.target.value)}
                placeholder="어떤 느낌의 음악을 찾고 있나요?"
              />
            </label>
            <p className="field-note">
              현재 음악 설명은 저장만 합니다. 추천에는 아래 선택 조건이
              반영됩니다.
            </p>
            <label>
              행사명
              <input
                required
                maxLength={80}
                value={form.title}
                onChange={(e) => change('title', e.target.value)}
              />
            </label>
            {/* 공연 날짜 · 시작 시간 */}
            <div className="form-pair">
              <label>
                공연 날짜
                <input
                  type="date"
                  required
                  value={form.date}
                  onChange={(e) => change('date', e.target.value)}
                />
              </label>
              <label>
                시작 시간
                <input
                  type="time"
                  required
                  value={form.start}
                  onChange={(e) => change('start', e.target.value)}
                />
              </label>
            </div>
            <p className="field-note">
              샘플 가능일: 2026.10.15 / 10.22, 12:00–23:00
            </p>
            {/* 지역 · 행사 종류 */}
            <div className="form-pair">
              <Select
                label="지역"
                value={form.region}
                options={REGIONS}
                onChange={(v) => change('region', v)}
              />
              <Select
                label="행사 종류"
                value={form.type}
                options={TYPES}
                onChange={(v) => change('type', v)}
              />
            </div>
            <div className="form-pair">
              <label>
                최대 예산 (만 원)
                <input
                  type="number"
                  min="1"
                  max="100000"
                  required
                  value={form.budget}
                  onChange={(e) => change('budget', e.target.value)}
                />
              </label>
              <label>
                공연 시간 (분)
                <input
                  type="number"
                  min="1"
                  max="1440"
                  required
                  value={form.minutes}
                  onChange={(e) => change('minutes', e.target.value)}
                />
              </label>
            </div>
            {/* 구분선 아래: 점수 계산에 쓰는 음악 스타일 조건 */}
            <div className="form-divider" />
            <div className="form-pair">
              <Select
                label="희망 장르"
                value={form.genre}
                options={GENRES}
                onChange={(v) => change('genre', v)}
              />
              <Select
                label="분위기"
                value={form.mood}
                options={MOODS}
                onChange={(v) => change('mood', v)}
              />
            </div>
            <div className="form-pair">
              <Select
                label="템포"
                value={form.tempo}
                options={TEMPOS}
                onChange={(v) => change('tempo', v)}
              />
              <Select
                label="리듬"
                value={form.rhythm}
                options={RHYTHMS}
                onChange={(v) => change('rhythm', v)}
              />
            </div>
            <p className="field-note">
              SLOW ≤89 · MEDIUM 90–119 · FAST ≥120 BPM
            </p>
            {error && (
              <p
                role="alert"
                className="error"
              >
                {error}
              </p>
            )}
            <button
              className="primary full"
              type="submit"
            >
              <Icon
                name="search"
                size={18}
              />{' '}
              조건으로 TOP 5 찾기
            </button>
          </form>
        </aside>
        {/* 추천 결과 영역 (검색 후 화면 낭독기 포커스 이동 대상) */}
        <section
          className="match-results"
          ref={resultsRef}
          tabIndex={-1}
          aria-label="추천 결과"
        >
          <div className="model-note">
            <Icon
              name="music"
              size={19}
            />
            <p>
              <strong>지금은 조건 매칭을 체험할 수 있어요.</strong>
              <br />
              CLAP·오디오 분석은 연결 전입니다. 점수는 가상 프로필의 태그와 샘플
              BPM으로 계산하며 성공 확률이 아닙니다.
            </p>
          </div>
          {/* 아직 검색 전: 안내 화면 */}
          {!report ? (
            <div className="empty-state match-empty">
              <div className="empty-icon">
                <Icon
                  name="search"
                  size={32}
                />
              </div>
              <h2>찾는 스타일을 말해주세요!</h2>
              <p>
                왼쪽 조건을 입력하면 추천 결과와
                <br />왜 어울리는지에 대한 근거를 보여드려요.
              </p>
              <div className="tags">
                <Tag>예산</Tag>
                <Tag>지역</Tag>
                <Tag>음악 스타일</Tag>
                <Tag>템포</Tag>
              </div>
            </div>
          ) : (
            <>
              {/* 검색 후: 조건 요약 → 추천 카드 목록 → 제외 이유 */}
              <div className="results-heading">
                <div>
                  <p className="eyebrow">YOUR SHORTLIST</p>
                  <h2>
                    맞춤 아티스트 <em>{report.results.length}</em>
                  </h2>
                </div>
                <Tag>동일 가중치 · 단순 평균</Tag>
              </div>
              <div className="search-receipt">
                <strong>{search.title}</strong>
                <p>
                  {search.date} {search.start} · {search.region} ·{' '}
                  {search.minutes}분 · 최대 {search.budget}만 원
                </p>
                <small>
                  전체 {ARTISTS.length}명 → 필수 조건 통과 {report.passed}명 →
                  최대 5명 추천
                </small>
              </div>
              <div className="results-list">
                {report.results.map((a, i) => (
                  <ArtistResult
                    key={a.id}
                    artist={a}
                    index={i}
                    next={report.results[i + 1]}
                    saved={saved.includes(a.id)}
                    toggleSaved={toggleSaved}
                  />
                ))}
              </div>
              {/* 추천 후보가 하나도 없을 때 */}
              {!report.results.length && (
                <div className="empty-state">
                  <h3>조건을 충족한 추천 후보가 없어요.</h3>
                  <p>
                    날짜·예산·지역을 조정하거나 아래 제외 이유를 확인해 주세요.
                  </p>
                </div>
              )}
              {/* 필수 조건 미충족으로 제외된 후보와 그 이유 */}
              <details className="panel excluded">
                <summary>
                  추천에서 제외된 이유 확인{' '}
                  <span>{report.excluded.length}명</span>
                </summary>
                {report.excluded.map((a) => (
                  <p key={a.id}>
                    <b>{a.name}</b>
                    <br />
                    {a.checks
                      .filter((c) => !c.pass)
                      .map((c) => c.label)
                      .join(' · ')}{' '}
                    조건 미충족
                  </p>
                ))}
                {report.zeroCount > 0 && (
                  <p>
                    필수 조건을 통과했지만 적합도 0점인 후보 {report.zeroCount}
                    명은 추천에서 제외했습니다.
                  </p>
                )}
                {!report.excluded.length && (
                  <p>필수 조건에서 제외된 후보가 없습니다.</p>
                )}
              </details>
            </>
          )}
        </section>
      </div>
    </>
  );
}

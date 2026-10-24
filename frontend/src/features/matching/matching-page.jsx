import { useEffect, useMemo, useState, useRef } from 'react';
import {
  DEFAULT_EVENT,
  GENRES,
  MOODS,
  REGIONS,
  TYPES,
  TEMPOS,
  RHYTHMS,
  validateEvent,
} from './matching.js';
import Icon from '../../components/common/icon.jsx';
import PageHeading from '../../components/common/page-heading.jsx';
import Select from '../../components/common/select.jsx';
import StagePhotos from '../../components/common/stage-photos.jsx';
import './matching-stage.css';
import { eventOptionLabel, eventToSearch } from './event-to-search.js';
import { sortEvents } from '../my-page/organizer-events.js';
import { toDateKey } from '../my-page/availability.js';
import SearchModeSwitch from './components/search-mode-switch.jsx';
import SongSearch from './components/song-search.jsx';
import SongResultList from './components/song-result-list.jsx';
import useSongPlayer from './use-song-player.js';
import { buildSongResults } from './song-results.js';

// 같은 행사는 1분에 한 번만 다시 추천받을 수 있음 (계약의 429 규칙과 같은 간격)
const RETRY_GAP_MS = 60 * 1000;
// 추천을 받은 뒤 바뀌면 '조건 바뀜' 안내를 띄우는 칸
const EVENT_KEYS = ['description', 'title', 'date', 'start', 'region', 'type', 'budget', 'minutes', 'genre', 'mood', 'tempo', 'rhythm'];

// 행사 관계자용 매칭 화면: 왼쪽 행사 조건 입력, 오른쪽 추천 결과
// search: 마지막으로 추천받은 조건 + searchedAt(받은 시각)·eventKey(어느 행사인지) (App이 보관하므로 다른 화면에 다녀와도 결과 유지)
// saved·toggleSaved: 관심 아티스트 (마이페이지 '관심 아티스트'와 같은 목록)
// events: 마이페이지 '내 행사'에 등록한 행사 목록, onGoToEvents: 내 행사 탭으로 이동 (NSU-95)
export default function Matching({ saved, toggleSaved, search, setSearch, events = [], onGoToEvents }) {
  // form: 입력 중인 조건 (검색 버튼을 눌러야 search에 반영)
  const [form, setForm] = useState(search || DEFAULT_EVENT);
  const [error, setError] = useState('');
  // 불러온 행사 id와 안내 { name, skipped }
  const [importId, setImportId] = useState('');
  const [importNote, setImportNote] = useState(null);
  const today = toDateKey(new Date());
  // 다가오는 행사를 먼저(가까운 날짜순), 지난 행사는 맨 아래로
  const sortedEvents = sortEvents(events);
  const importOptions = [
    ...sortedEvents.filter((ev) => ev.date >= today),
    ...sortedEvents.filter((ev) => ev.date < today),
  ];

  // 내 행사 하나를 골라 매칭 조건 칸을 채움 ('' = 직접 입력으로 되돌림, 입력값은 그대로)
  function importEvent(id) {
    setImportId(id);
    if (!id) {
      setImportNote(null);
      return;
    }
    const event = events.find((ev) => ev.id === id);
    if (!event) return;
    const { search: next, skipped } = eventToSearch(event, form);
    setForm(next);
    setError('');
    setImportNote({ name: event.name, skipped });
  }
  const resultsRef = useRef(null);
  // 찾는 방법 (NSU-111): 'event' 행사 설명으로 / 'song' 참고 곡으로
  const [mode, setMode] = useState('event');
  // 참고 곡으로 찾기 결과가 있는지 (배경 사진 전환용)
  const [songDone, setSongDone] = useState(false);
  const modeSwitch = (
    <SearchModeSwitch
      mode={mode}
      onChange={setMode}
    />
  );
  // 행사 설명으로 찾기 결과 (NSU-111): 참고 곡으로 찾기와 같은 목록 형식의 체험용 결과
  // 받은 시각이 같으면 같은 결과를 보여 줌. 조건을 바꾸면 '조건 바뀜' 안내
  const searchedAt = search?.searchedAt;
  const results = useMemo(() => (searchedAt ? buildSongResults(new Date(searchedAt)) : null), [searchedAt]);
  const eventChanged = Boolean(search) && EVENT_KEYS.some((k) => String(form[k]) !== String(search[k]));
  const [retryNote, setRetryNote] = useState('');
  // 행사별로 마지막에 추천받은 시각 (같은 행사만 1분 간격, 다른 행사는 바로 받을 수 있음)
  const lastByEventRef = useRef({});
  const player = useSongPlayer();
  const { stop } = player;
  // 참고 곡으로 찾기로 바꾸면 이쪽 소리를 끔
  useEffect(() => {
    if (mode !== 'event') stop();
  }, [mode, stop]);
  // 입력칸 하나의 값을 바꿈
  const change = (name, value) =>
    setForm((prev) => ({ ...prev, [name]: value }));
  // 추천받기: 입력 확인 → 같은 행사는 1분 간격 → 조건과 받은 시각 저장 후 결과 영역으로 포커스 이동
  function submit(e) {
    e?.preventDefault();
    const message = validateEvent(form);
    setError(message);
    if (message) return;
    // 어느 행사인지: '내 행사'에서 불러왔으면 그 행사, 직접 입력했으면 행사명으로 구분
    const eventKey = importId ? `event:${importId}` : `title:${form.title.trim()}`;
    const lastAt = lastByEventRef.current[eventKey] ?? (search?.eventKey === eventKey ? search.searchedAt : 0);
    const wait = lastAt ? RETRY_GAP_MS - (Date.now() - lastAt) : 0;
    if (wait > 0) {
      setRetryNote(`같은 행사는 1분에 한 번만 다시 받을 수 있어요. ${Math.ceil(wait / 1000)}초 뒤에 다시 눌러 주세요.`);
      return;
    }
    stop();
    setRetryNote('');
    const now = Date.now();
    lastByEventRef.current[eventKey] = now;
    setSearch({ ...form, searchedAt: now, eventKey });
    setTimeout(() => resultsRef.current?.focus(), 0);
  }
  // 홈과 같은 무대 배경 위에 화면을 올림. 검색 전에는 파란 무대, 결과가 나오면 공연 사진으로 바뀜
  return (
    <div className="stage-scope match-stage">
      <StagePhotos active={(mode === 'song' ? songDone : results) ? 2 : 1} />
      <div className="match-stage-inner">
        <PageHeading
          eyebrow="FIND YOUR MATCH"
          title="원하는 스타일을 알려 주세요"
          description="필수 조건으로 걸러내고, 항목별 근거로 비교하세요."
        />
        <div
          className="matching-layout"
          hidden={mode === 'song'}
        >
          {/* 행사 조건 입력 폼 */}
          <aside>
            <form
              className="panel match-form"
              onSubmit={submit}
            >
              {modeSwitch}
              <div className="form-heading">
                <h2>행사 조건</h2>
                <button
                  type="button"
                  className="text-button"
                  onClick={() => {
                    setForm({ ...DEFAULT_EVENT });
                    setError('');
                    setImportId('');
                    setImportNote(null);
                  }}
                >
                  예시 채우기
                </button>
              </div>
              {/* 내 행사에서 불러오기 (요구사항 AI-MATCH-050: 매칭은 등록한 행사 기준) */}
              <div className="match-import">
                {events.length ? (
                  <>
                    <label>
                      내 행사에서 불러오기
                      <select
                        value={importId}
                        onChange={(e) => importEvent(e.target.value)}
                      >
                        <option value="">직접 입력</option>
                        {importOptions.map((ev) => (
                          <option
                            key={ev.id}
                            value={ev.id}
                          >
                            {eventOptionLabel(ev, today)}
                          </option>
                        ))}
                      </select>
                    </label>
                    {importNote && (
                      <div
                        className="match-import-note"
                        role="status"
                      >
                        <p>
                          <strong>'{importNote.name}'</strong> 정보를 불러왔어요. 분위기·템포·리듬은
                          행사 정보에 없어서 직접 골라 주세요.
                        </p>
                        {importNote.skipped.length > 0 && (
                          <ul>
                            {importNote.skipped.map((text) => (
                              <li key={text}>{text}</li>
                            ))}
                          </ul>
                        )}
                      </div>
                    )}
                  </>
                ) : (
                  <p className="match-import-empty">
                    마이페이지에 행사를 등록해 두면 여기서 바로 불러올 수 있어요.{' '}
                    {onGoToEvents && (
                      <button
                        type="button"
                        className="text-button"
                        onClick={onGoToEvents}
                      >
                        행사 등록하러 가기 →
                      </button>
                    )}
                  </p>
                )}
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
                실제 추천에서는 AI가 이 설명을 해석해 곡을 찾아요. 체험판 결과는 예시 값이에요.
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
              {/* 구분선 아래: 음악 스타일 조건 (템포·리듬은 추천 이유 슬라이드에서 비교) */}
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
                BPM 기준: SLOW 89 이하, MEDIUM 90-119, FAST 120 이상
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
                이 조건으로 추천받기
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
                <strong>지금은 행사 설명으로 찾기 체험판이에요.</strong>
                <br />
                CLAP 음악 유사도 추천은 연결 전이라 결과는 예시 값이에요. 추천 곡은 실제 음원 대신 그 곡의 BPM과
                박자를 소리로 들려줘요.
              </p>
            </div>
            {/* 아직 추천 전: 안내 화면 */}
            {!results ? (
              <div className="empty-state match-empty">
                <div className="empty-icon">
                  <Icon
                    name="search"
                    size={32}
                  />
                </div>
                <h2>찾는 스타일을 입력해 주세요</h2>
                <p>
                  왼쪽 조건을 입력하면 어울리는 곡과
                  <br />왜 어울리는지에 대한 근거를 보여드려요.
                </p>
              </div>
            ) : (
              <SongResultList
                key={results.recommendationId}
                results={{ ...results, isEventChanged: eventChanged }}
                basis={{ kind: 'event', tempo: search.tempo, event: search }}
                player={player}
                onRetry={() => submit()}
                retryNote={retryNote}
                saved={saved}
                toggleSaved={toggleSaved}
              />
            )}
          </section>
        </div>
        {/* 참고 곡으로 찾기: 다른 방법으로 바꿔도 올린 곡과 결과를 기억하도록 숨겨만 둠 */}
        <SongSearch
          hidden={mode !== 'song'}
          modeSwitch={modeSwitch}
          form={form}
          setForm={setForm}
          onResultsChange={setSongDone}
          saved={saved}
          toggleSaved={toggleSaved}
        />
      </div>
    </div>
  );
}

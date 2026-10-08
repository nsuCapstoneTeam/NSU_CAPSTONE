import { useRef, useState } from 'react';
import { GENRES, REGIONS } from '../../matching/matching.js';
import {
  ARTIST_TYPES,
  EVENT_TYPES,
  VISIBILITIES,
  eventProblems,
} from '../organizer-events.js';

// 행사 등록·수정 폼 (NSU-56, 요구사항 EVT-042 필수 15개)
// - 저장을 누르면 고칠 항목을 위쪽 안내 상자에 모아 보여 주고, 항목을 누르면 그 칸으로 이동
// - 안내 상자가 뜬 뒤에는 입력할 때마다 목록이 줄어듦
// initial: 처음 값 (수정이면 저장된 행사), onSave(values): 저장, onCancel: 취소

// 라벨 옆 필수 표시 (화면낭독기는 입력칸의 required로 '필수'를 읽으므로 *는 숨김)
function Req() {
  return (
    <span className="event-required" aria-hidden="true">
      *
    </span>
  );
}

export default function EventForm({ initial, isEdit, onSave, onCancel }) {
  const [form, setForm] = useState(initial);
  // 저장을 한 번 눌러 봤는지 (그 전에는 빨간 표시를 하지 않음)
  const [tried, setTried] = useState(false);
  const summaryRef = useRef(null);

  const problems = eventProblems(form);
  const problemIds = new Set(problems.map((p) => p.id));
  const invalid = (id) => tried && problemIds.has(id);
  const update = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));

  function toggleGenre(genre) {
    update(
      'genres',
      form.genres.includes(genre) ? form.genres.filter((g) => g !== genre) : [...form.genres, genre],
    );
  }

  function submit(e) {
    e.preventDefault();
    setTried(true);
    if (problems.length) {
      // 안내 상자로 포커스 → 화면낭독기가 고칠 항목 수를 바로 읽음
      window.requestAnimationFrame(() => summaryRef.current?.focus());
      return;
    }
    onSave(form);
  }

  // 안내 상자에서 항목을 누르면 그 입력칸으로 이동
  function goTo(id) {
    const el = document.getElementById(`event-${id}`);
    el?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    el?.focus({ preventScroll: true });
  }

  return (
    <form className="event-form" onSubmit={submit} noValidate aria-labelledby="event-form-title">
      <h3 id="event-form-title">{isEdit ? '행사 정보 수정' : '새 행사 등록'}</h3>
      <p className="event-form-note">
        <Req /> 표시는 필수 항목이에요. 등록한 행사는 이 브라우저에만 저장돼요.
      </p>

      {/* 고칠 항목 안내 (저장을 눌렀는데 문제가 있을 때만) */}
      {tried && problems.length > 0 && (
        <div className="event-problems" role="alert" tabIndex={-1} ref={summaryRef}>
          <strong>고칠 항목이 {problems.length}개 있어요</strong>
          <ul>
            {problems.map((p) => (
              <li key={p.id}>
                <button type="button" className="text-button" onClick={() => goTo(p.id)}>
                  {p.label}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 1. 기본 정보 */}
      <fieldset className="event-section">
        <legend>기본 정보</legend>
        <label>
          <span>
            행사명 <Req />
          </span>
          <input
            id="event-name"
            required
            maxLength={60}
            value={form.name}
            onChange={(e) => update('name', e.target.value)}
            placeholder="예: 2026 가을 대학 축제"
            aria-invalid={invalid('name')}
          />
        </label>
        <div className="form-pair">
          <label>
            <span>
              행사 종류 <Req />
            </span>
            <select
              id="event-eventType"
              required
              value={form.eventType}
              onChange={(e) => update('eventType', e.target.value)}
              aria-invalid={invalid('eventType')}
            >
              <option value="">선택해 주세요</option>
              {EVENT_TYPES.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </label>
          <label>
            <span>
              행사 날짜 <Req />
            </span>
            <input
              id="event-date"
              type="date"
              required
              value={form.date}
              onChange={(e) => update('date', e.target.value)}
              aria-invalid={invalid('date')}
            />
          </label>
        </div>
        <div className="form-pair">
          <label>
            <span>
              공연 시작 시간 <Req />
            </span>
            <input
              id="event-startTime"
              type="time"
              required
              value={form.startTime}
              onChange={(e) => update('startTime', e.target.value)}
              aria-invalid={invalid('startTime')}
            />
          </label>
          <label>
            <span>
              공연 종료 시간 <Req />
            </span>
            <input
              id="event-endTime"
              type="time"
              required
              value={form.endTime}
              onChange={(e) => update('endTime', e.target.value)}
              aria-invalid={invalid('endTime')}
            />
          </label>
        </div>
        <div className="form-pair">
          <label>
            <span>
              장소 <Req />
            </span>
            <input
              id="event-venue"
              required
              maxLength={80}
              value={form.venue}
              onChange={(e) => update('venue', e.target.value)}
              placeholder="예: 대운동장 특설 무대"
              aria-invalid={invalid('venue')}
            />
          </label>
          <label>
            <span>
              지역 <Req />
            </span>
            <select
              id="event-region"
              required
              value={form.region}
              onChange={(e) => update('region', e.target.value)}
              aria-invalid={invalid('region')}
            >
              <option value="">선택해 주세요</option>
              {REGIONS.map((r) => (
                <option key={r}>{r}</option>
              ))}
            </select>
          </label>
        </div>
      </fieldset>

      {/* 2. 원하는 공연 */}
      <fieldset className="event-section">
        <legend>원하는 공연</legend>
        <div className="form-pair">
          <label>
            <span>
              희망 아티스트 형태 <Req />
            </span>
            <select
              id="event-artistType"
              required
              value={form.artistType}
              onChange={(e) => update('artistType', e.target.value)}
              aria-invalid={invalid('artistType')}
            >
              <option value="">선택해 주세요</option>
              {ARTIST_TYPES.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </label>
          <label>
            <span>
              공연 시간 (분) <Req />
            </span>
            <input
              id="event-performanceMinutes"
              type="number"
              inputMode="numeric"
              min="1"
              required
              value={form.performanceMinutes}
              onChange={(e) => update('performanceMinutes', e.target.value)}
              placeholder="예: 40"
              aria-invalid={invalid('performanceMinutes')}
            />
          </label>
        </div>
        <fieldset className="event-genres" aria-invalid={invalid('genres')}>
          <legend>
            희망 장르 <Req /> <small>여러 개 고를 수 있어요</small>
          </legend>
          <div className="event-chip-row">
            {GENRES.map((g, i) => (
              <label key={g} className="event-chip">
                <input
                  id={i === 0 ? 'event-genres' : undefined}
                  type="checkbox"
                  checked={form.genres.includes(g)}
                  onChange={() => toggleGenre(g)}
                />
                {g}
              </label>
            ))}
          </div>
        </fieldset>
        <label>
          <span>
            예상 관객 수 (명) <Req />
          </span>
          <input
            id="event-audience"
            type="number"
            inputMode="numeric"
            min="1"
            required
            value={form.audience}
            onChange={(e) => update('audience', e.target.value)}
            placeholder="예: 500"
            aria-invalid={invalid('audience')}
          />
        </label>
      </fieldset>

      {/* 3. 예산 */}
      <fieldset className="event-section">
        <legend>예산 (만 원)</legend>
        <div className="form-pair">
          <label>
            <span>
              최소 <Req />
            </span>
            <input
              id="event-budgetMin"
              type="number"
              inputMode="numeric"
              min="1"
              required
              value={form.budgetMin}
              onChange={(e) => update('budgetMin', e.target.value)}
              placeholder="예: 30"
              aria-invalid={invalid('budgetMin')}
            />
          </label>
          <label>
            <span>
              최대 <Req />
            </span>
            <input
              id="event-budgetMax"
              type="number"
              inputMode="numeric"
              min="1"
              required
              value={form.budgetMax}
              onChange={(e) => update('budgetMax', e.target.value)}
              placeholder="예: 50"
              aria-invalid={invalid('budgetMax')}
            />
          </label>
        </div>
      </fieldset>

      {/* 4. 설명·공개 범위 */}
      <fieldset className="event-section">
        <legend>설명·공개 범위</legend>
        <label>
          <span>
            행사 설명 <Req />
          </span>
          <textarea
            id="event-description"
            required
            maxLength={1000}
            rows={4}
            value={form.description}
            onChange={(e) => update('description', e.target.value)}
            placeholder="행사 분위기, 관객층, 원하는 공연 느낌을 자유롭게 적어 주세요."
            aria-invalid={invalid('description')}
          />
        </label>
        <fieldset className="event-visibility">
          <legend>
            공개 범위 <Req />
          </legend>
          {VISIBILITIES.map((v, i) => (
            <label key={v.value} className="event-radio">
              <input
                id={i === 0 ? 'event-visibility' : undefined}
                type="radio"
                name="event-visibility"
                value={v.value}
                checked={form.visibility === v.value}
                onChange={() => update('visibility', v.value)}
              />
              <span>
                <strong>{v.label}</strong>
                <small>{v.description}</small>
              </span>
            </label>
          ))}
        </fieldset>
      </fieldset>

      <div className="event-form-actions">
        <button className="primary" type="submit">
          {isEdit ? '수정 내용 저장' : '행사 등록'}
        </button>
        <button className="secondary" type="button" onClick={onCancel}>
          취소
        </button>
      </div>
    </form>
  );
}

import { useRef, useState } from 'react';
import { GENRES } from '../../matching/matching.js';
import {
  ARTIST_TYPES,
  EVENT_TYPES,
  NOTES_MAX,
  VISIBILITIES,
  eventProblems,
  spanMinutes,
} from '../organizer-events.js';
import PlaceField from './place-field.jsx';
import StagePlanField from './stage-plan-field.jsx';
import './event-form.css';

// 행사 등록·수정 폼 (NSU-56·NSU-115, 요구사항 EVT-042 필수 15개)
// - 장소: 주소 검색으로 장소와 지역을 함께 정함 (place-field.jsx)
// - 행사 설명(추천에 사용) 다음에 특이사항·무대 도면(추천에 쓰지 않음)을 이어서 받음
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
  // 비워 둘 시간의 길이와 공연 시간 (비우면 시간 전체)
  const span = spanMinutes(form.startTime, form.endTime);
  const minutes = Number(form.performanceMinutes) > 0 ? Number(form.performanceMinutes) : span;

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
        {/* 시간: 아티스트가 비워 둘 시간(이동·리허설·대기 포함)과 그 안의 공연 시간을 한곳에 */}
        <fieldset className="event-time">
          <legend>
            아티스트가 비워 둘 시간 <Req />
          </legend>
          <div className="event-time-row">
            <label>
              <span>시작</span>
              <input
                id="event-startTime"
                type="time"
                required
                value={form.startTime}
                onChange={(e) => update('startTime', e.target.value)}
                aria-invalid={invalid('startTime')}
                aria-describedby="event-time-help"
              />
            </label>
            <label>
              <span>종료</span>
              <input
                id="event-endTime"
                type="time"
                required
                value={form.endTime}
                onChange={(e) => update('endTime', e.target.value)}
                aria-invalid={invalid('endTime')}
                aria-describedby="event-time-help"
              />
            </label>
            <label>
              <span>
                공연 시간 <small>선택</small>
              </span>
              <span className="event-unit-input">
                <input
                  id="event-performanceMinutes"
                  type="number"
                  inputMode="numeric"
                  min="1"
                  max={span || undefined}
                  value={form.performanceMinutes}
                  onChange={(e) => update('performanceMinutes', e.target.value)}
                  placeholder={span ? `비우면 ${span}` : '예: 40'}
                  aria-invalid={invalid('performanceMinutes')}
                  aria-describedby={span > 0 ? 'event-time-help event-time-summary' : 'event-time-help'}
                />
                <span aria-hidden="true">분</span>
              </span>
            </label>
          </div>
          <small id="event-time-help">
            이동·리허설·대기까지 포함해 주세요. 공연 시간은 그 안에서 실제로 무대에 서는 길이이고, 비우면 시간 전체로
            저장돼요.
          </small>
          {span > 0 && (
            <p
              id="event-time-summary"
              className="event-time-summary"
            >
              {form.startTime}~{form.endTime} 중 <strong>{minutes}분 공연</strong>
            </p>
          )}
        </fieldset>
        <PlaceField
          form={form}
          update={update}
          invalid={invalid}
        />
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

      {/* 4. 행사 설명·현장 정보: 설명은 추천에 쓰고, 특이사항·도면은 아티스트에게 보여 주기만 함 */}
      <fieldset className="event-section">
        <legend>
          행사 설명·현장 정보 <small>아티스트가 행사 상세에서 봐요</small>
        </legend>
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
            placeholder="예: 가을 축제 본무대에 설 밴드를 찾아요. 학생 관객이 대부분이라 함께 따라 부를 수 있는 곡이 좋아요."
            aria-invalid={invalid('description')}
            aria-describedby="event-description-help"
          />
          <small id="event-description-help">
            행사 분위기, 관객층, 원하는 음악을 적어 주세요. AI가 이 글을 읽고 어울리는 곡을 찾아요.
          </small>
        </label>
        <label>
          <span>
            특이사항 <small>선택</small>
          </span>
          <textarea
            id="event-notes"
            maxLength={NOTES_MAX}
            rows={4}
            value={form.notes}
            onChange={(e) => update('notes', e.target.value)}
            placeholder="예: 리허설은 18:00부터 30분이에요. 대기실이 있고 저녁 식사를 드려요."
            aria-describedby="event-notes-help event-notes-count"
          />
          <span className="event-field-foot">
            <small id="event-notes-help">
              리허설 시간, 대기실, 식사, 교통비, 숙박, 현장 장비처럼 현장 안내를 적어 주세요. 추천에는 쓰지 않아요.
            </small>
            <small id="event-notes-count">
              {form.notes.length} / {NOTES_MAX}
            </small>
          </span>
        </label>
        <StagePlanField
          value={form.stagePlan}
          onChange={(value) => update('stagePlan', value)}
        />
        <p className="event-section-note">
          행사 설명은 추천에 쓰고, 특이사항과 무대 도면은 추천에 쓰지 않아요. 특이사항이나 도면을 고쳐도 받은 추천은
          그대로예요.
        </p>
      </fieldset>

      {/* 5. 공개 범위 */}
      <fieldset className="event-section">
        <legend>공개 범위</legend>
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

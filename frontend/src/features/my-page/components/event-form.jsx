import { useEffect, useRef, useState } from 'react';
import SoundWave from '../../../components/common/sound-wave.jsx';
import { noticeFor } from '../../events/events-api.js';
import {
  ARTIST_TYPES,
  DETAIL_FLAGS,
  DETAIL_TEXTS,
  DETAIL_TEXT_MAX,
  EVENT_GENRES,
  EVENT_TYPES,
  GENRE_MAX,
  NOTES_MAX,
  VISIBILITIES,
  endDateFor,
  endsNextDay,
  eventProblems,
  spanMinutes,
} from '../organizer-events.js';
import PlaceField from './place-field.jsx';
import StagePlanField from './stage-plan-field.jsx';
import './event-form.css';

// 행사 등록·수정 폼 (NSU-56·NSU-115·NSU-125, 요구사항 EVT-042 필수 15개)
// - 장소: 주소 검색으로 장소와 지역을 함께 정함 (place-field.jsx). 서버 모드에서는 주소를 저장하지 않음
// - 종료가 시작보다 이르면 다음 날 끝나는 행사로 저장 (22:00~02:00)
// - 현장 정보(리허설·무대·장비·제공 여부·기타 조건)는 아티스트에게 보여 주기만 하고 추천에 쓰지 않음
// - 저장을 누르면 고칠 항목을 위쪽 안내 상자에 모아 보여 주고, 항목을 누르면 그 칸으로 이동
// - 서버에 보내는 동안 버튼에 소리 막대를 띄우고, 실패하면 위쪽에 이유와 할 일을 보여 줌 (입력은 그대로)
// initial: 처음 값 (수정이면 저장된 행사)
// onSave(values, { onRetry }): 저장 (Promise). 실패하면 EventsApiError를 던짐
// onCancel: 취소, onShowList: 실패 안내의 '내 행사 보기', serverMode: 서버에 저장하는지

// 제공 여부 세 갈래 [값, 이름]
const FLAG_CHOICES = [
  [true, '제공'],
  [false, '안 함'],
  [null, '미정'],
];

// 실패 안내의 할 일 버튼 이름 (events-api.js의 action)
const FAILURE_ACTION_LABELS = { date: '날짜 고치기', list: '내 행사 보기', retry: '다시 시도' };

// '2026-10-31' → '10월 31일'
const monthDay = (date) => {
  const [, m, d] = String(date).split('-').map(Number);
  return m && d ? `${m}월 ${d}일` : '';
};

// 라벨 옆 필수 표시 (화면낭독기는 입력칸의 required로 '필수'를 읽으므로 *는 숨김)
function Req() {
  return (
    <span className="event-required" aria-hidden="true">
      *
    </span>
  );
}

export default function EventForm({ initial, isEdit, onSave, onCancel, onShowList, serverMode = false }) {
  const [form, setForm] = useState(initial);
  // 저장을 한 번 눌러 봤는지 (그 전에는 빨간 표시를 하지 않음)
  const [tried, setTried] = useState(false);
  // 보내는 중: null | { retry: 0 } | { retry: n, total } (n번째로 다시 보내는 중)
  const [sending, setSending] = useState(null);
  // 서버 실패 안내 (noticeFor 결과)
  const [failure, setFailure] = useState(null);
  const summaryRef = useRef(null);
  const failureRef = useRef(null);
  // 폼이 닫힌 뒤 도착한 응답으로 상태를 바꾸지 않게 (개발 모드의 다시 붙이기에도 맞게 붙을 때 true)
  const alive = useRef(false);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const problems = eventProblems(form);
  const problemIds = new Set(problems.map((p) => p.id));
  const invalid = (id) => tried && problemIds.has(id);
  const update = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));
  const updateDetail = (key, value) =>
    setForm((prev) => ({ ...prev, details: { ...prev.details, [key]: value } }));
  const overnight = Boolean(form.startTime && form.endTime) && endsNextDay(form.startTime, form.endTime);
  const genreFull = form.genres.length >= GENRE_MAX;
  // 비워 둘 시간의 길이와 공연 시간 (비우면 시간 전체)
  const span = spanMinutes(form.startTime, form.endTime);
  const minutes = Number(form.performanceMinutes) > 0 ? Number(form.performanceMinutes) : span;

  function toggleGenre(genre) {
    update(
      'genres',
      form.genres.includes(genre) ? form.genres.filter((g) => g !== genre) : [...form.genres, genre],
    );
  }

  async function send() {
    if (sending) return;
    setFailure(null);
    setSending({ retry: 0 });
    try {
      await onSave(form, {
        onRetry: (retry, total) => alive.current && setSending({ retry, total }),
      });
    } catch (error) {
      if (!alive.current) return;
      setFailure(noticeFor(error));
      // 안내 상자로 포커스 → 화면낭독기가 실패 이유를 바로 읽음
      window.requestAnimationFrame(() => failureRef.current?.focus());
    } finally {
      if (alive.current) setSending(null);
    }
  }

  // 저장과 '다시 시도'가 같은 길: 지금 입력을 먼저 확인하고, 문제가 없을 때만 보냄
  function validateAndSend() {
    setTried(true);
    if (problems.length) {
      setFailure(null);
      // 안내 상자로 포커스 → 화면낭독기가 고칠 항목 수를 바로 읽음
      window.requestAnimationFrame(() => summaryRef.current?.focus());
      return;
    }
    send();
  }

  function submit(e) {
    e.preventDefault();
    validateAndSend();
  }

  // 실패 안내의 버튼: 날짜 칸으로, 내 행사 보기, 다시 시도
  function runFailureAction() {
    if (failure?.action === 'date') goTo('date');
    else if (failure?.action === 'list') onShowList?.();
    else if (failure?.action === 'retry') validateAndSend();
  }
  const failureActionLabel = FAILURE_ACTION_LABELS[failure?.action];

  const saveLabel = isEdit ? '수정 내용 저장' : '행사 등록';
  const sendingLabel = !sending
    ? saveLabel
    : sending.retry
      ? `연결이 끊겨 다시 보내는 중 (${sending.retry}/${sending.total})`
      : isEdit
        ? '저장하는 중…'
        : '등록하는 중…';

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
        <Req /> 표시는 필수 항목이에요.{serverMode ? '' : ' 등록한 행사는 이 브라우저에만 저장돼요.'}
      </p>

      {/* 서버 실패 안내: 무엇이 안 됐는지와 할 일. 입력한 내용은 그대로 둠 */}
      {failure && (
        <div
          className={`event-failure ${failure.tone === 'warn' ? 'is-warn' : ''}`}
          role="alert"
          tabIndex={-1}
          ref={failureRef}
        >
          <strong>{failure.title}</strong>
          {failure.body && <p>{failure.body}</p>}
          {failureActionLabel && (
            <button type="button" className="secondary" onClick={runFailureAction}>
              {failureActionLabel}
            </button>
          )}
        </div>
      )}

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
            이동, 리허설, 대기까지 포함해 주세요. 공연 시간은 그 안에서 실제로 무대에 서는 길이이고, 비우면 시간
            전체로 저장돼요.
          </small>
          {span > 0 && (
            <p
              id="event-time-summary"
              className="event-time-summary"
            >
              {form.startTime}~{overnight ? '다음 날 ' : ''}
              {form.endTime} 중 <strong>{minutes}분 공연</strong>
            </p>
          )}
          {overnight && (
            <p className="event-overnight" role="status">
              종료가 시작보다 일러서{' '}
              {form.date ? `다음 날(${monthDay(endDateFor(form.date, form.startTime, form.endTime))})` : '다음 날'}{' '}
              {form.endTime}까지로 저장해요.
            </p>
          )}
        </fieldset>
        <PlaceField
          form={form}
          update={update}
          invalid={invalid}
        />
        {serverMode && (
          <p className="event-section-note">
            주소는 지역을 정하는 데만 쓰고 저장하지 않아요. 아티스트에게는 장소 이름과 지역이 보여요.
          </p>
        )}
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
            희망 장르 <Req />{' '}
            <small id="event-genres-help">
              1~{GENRE_MAX}개 골라 주세요 ({form.genres.length}/{GENRE_MAX})
            </small>
          </legend>
          <div className="event-chip-row">
            {EVENT_GENRES.map((g, i) => {
              const checked = form.genres.includes(g);
              return (
                <label key={g} className={`event-chip ${!checked && genreFull ? 'is-disabled' : ''}`}>
                  <input
                    id={i === 0 ? 'event-genres' : undefined}
                    type="checkbox"
                    checked={checked}
                    disabled={!checked && genreFull}
                    onChange={() => toggleGenre(g)}
                    aria-describedby="event-genres-help"
                  />
                  {g}
                </label>
              );
            })}
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
              step="any"
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
              step="any"
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

      {/* 4. 행사 설명: 추천에 사용 */}
      <fieldset className="event-section">
        <legend>
          행사 설명 <small>AI가 읽고 어울리는 곡을 찾아요</small>
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
          <small id="event-description-help">행사 분위기, 관객층, 원하는 음악을 적어 주세요.</small>
        </label>
      </fieldset>

      {/* 5. 현장 정보: 모두 선택. 아티스트에게 보여 주기만 하고 추천에 쓰지 않음 (행사 API §3.4) */}
      <fieldset className="event-section">
        <legend>
          현장 정보 <small>모두 선택이에요. 추천에는 쓰지 않아요</small>
        </legend>
        <div className="event-details-grid">
          {DETAIL_TEXTS.map(([id, label, placeholder]) => (
            <label key={id}>
              <span>{label}</span>
              <input
                id={`event-details-${id}`}
                maxLength={DETAIL_TEXT_MAX}
                value={form.details[id]}
                onChange={(e) => updateDetail(id, e.target.value)}
                placeholder={placeholder}
                aria-invalid={invalid(`details-${id}`)}
              />
            </label>
          ))}
        </div>
        <div className="event-flags">
          {DETAIL_FLAGS.map(([id, label]) => (
            <fieldset key={id} className="event-flag">
              <legend>{label}</legend>
              <div className="event-flag-choices">
                {FLAG_CHOICES.map(([value, name]) => (
                  <label key={name}>
                    <input
                      type="radio"
                      name={`event-${id}`}
                      checked={form.details[id] === value}
                      onChange={() => updateDetail(id, value)}
                    />
                    <span>{name}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          ))}
        </div>
        <label>
          <span>
            기타 조건 <small>선택</small>
          </span>
          <textarea
            id="event-notes"
            maxLength={NOTES_MAX}
            rows={3}
            value={form.notes}
            onChange={(e) => update('notes', e.target.value)}
            placeholder="예: 주차는 2대까지 돼요. 공연 중 사진 촬영이 있어요."
            aria-describedby="event-notes-help event-notes-count"
          />
          <span className="event-field-foot">
            <small id="event-notes-help">위 칸에 없는 현장 안내를 적어 주세요.</small>
            <small id="event-notes-count">
              {form.notes.length} / {NOTES_MAX}
            </small>
          </span>
        </label>
        {serverMode ? (
          <p className="event-section-note">
            무대 도면은 아직 서버에 저장할 곳이 없어 받지 않아요. 필요하면 기타 조건에 적어 주세요.
          </p>
        ) : (
          <StagePlanField
            value={form.stagePlan}
            onChange={(value) => update('stagePlan', value)}
          />
        )}
      </fieldset>

      {/* 6. 공개 범위 */}
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

      {/* 보내는 중 상태를 화면낭독기에 알림 (버튼 글자와 같은 내용) */}
      <p className="event-live" role="status">
        {sending ? sendingLabel : ''}
      </p>
      <div className="event-form-actions">
        <button
          className={`primary ${sending ? 'is-sending' : ''}`}
          type="submit"
          aria-disabled={Boolean(sending)}
        >
          {sending && <SoundWave />}
          {sendingLabel}
        </button>
        <button className="secondary" type="button" onClick={onCancel} disabled={Boolean(sending)}>
          취소
        </button>
      </div>
    </form>
  );
}

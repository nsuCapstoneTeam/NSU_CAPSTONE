import { useEffect, useRef, useState } from 'react';
import SoundWave from '../../../components/common/sound-wave.jsx';
import Tag from '../../../components/common/tag.jsx';
import { noticeFor } from '../../events/events-api.js';
import { dayTitle, toDateKey } from '../availability.js';
import {
  DETAIL_FLAGS,
  DETAIL_TEXTS,
  EMPTY_EVENT,
  VISIBILITIES,
  budgetText,
  endsNextDay,
  isPast,
  normalizeEvent,
  sortEvents,
  toFormValues,
} from '../organizer-events.js';
import EventForm from './event-form.jsx';
import './events-tab.css';

// 마이페이지 '내 행사' 탭 (행사 관계자, NSU-56·NSU-125 / 요구사항 EVT-041·EVT-042)
// 행사 목록(다가오는 행사·지난 행사) + 등록·수정·삭제.
// 저장은 myEvents(use-my-events.js)가 맡음: 서버 모드는 행사 API, 체험 모드는 이 브라우저
// 취소된 행사는 '취소됨'으로 보여 주고 삭제만 할 수 있음 (취소하기 버튼은 아직 없음)

const visibilityLabel = (value) => VISIBILITIES.find((v) => v.value === value)?.label ?? value;
const isCancelled = (event) => event.status === 'CANCELLED';

// 제공 여부를 '제공'·'제공 안 함' 두 줄로 (미정은 보여 주지 않음)
function flagLists(details = {}) {
  const pick = (value) => DETAIL_FLAGS.filter(([id]) => details[id] === value).map(([, label]) => label);
  return { yes: pick(true), no: pick(false) };
}

export default function EventsTab({ myEvents, notify }) {
  const { events, status, error, reload, mode } = myEvents;
  // 열린 폼: null(닫힘) | { id: null }(새 행사) | { id: '...' }(수정)
  const [editing, setEditing] = useState(null);
  // 보고 있는 목록: 'upcoming' | 'past'
  const [period, setPeriod] = useState('upcoming');
  // 삭제를 한 번 더 확인 중인 행사 id, 삭제 중인 행사 id
  // 삭제는 한 번에 하나만: 삭제 중에는 다른 행사의 삭제 버튼을 막아 진행 표시가 섞이지 않게 함
  const [confirmId, setConfirmId] = useState(null);
  const [removingId, setRemovingId] = useState(null);
  // 폼을 닫은 뒤 포커스를 돌려줄 대상 id ('new' = 새 행사 등록 버튼)
  const focusTarget = useRef(null);
  const newButtonRef = useRef(null);

  const today = toDateKey(new Date());
  const upcoming = sortEvents(events.filter((e) => !isPast(e, today)));
  // 지난 행사는 최근 것이 위로
  const past = sortEvents(events.filter((e) => isPast(e, today))).reverse();
  const list = period === 'upcoming' ? upcoming : past;
  const editingEvent = editing?.id ? events.find((e) => e.id === editing.id) : null;

  // 폼을 닫거나 저장한 뒤 포커스를 알맞은 곳으로 (키보드 사용자가 길을 잃지 않게)
  useEffect(() => {
    const target = focusTarget.current;
    if (!target) return;
    focusTarget.current = null;
    if (target === 'new') newButtonRef.current?.focus();
    else document.getElementById(`event-card-${target}`)?.focus();
  });

  // 삭제 확인이 열리면 '취소'에 포커스 (실수로 바로 삭제되지 않게)
  useEffect(() => {
    if (confirmId) document.getElementById(`event-confirm-cancel-${confirmId}`)?.focus();
  }, [confirmId]);

  // 폼이 열리면 첫 입력칸으로
  useEffect(() => {
    if (editing) document.getElementById('event-name')?.focus();
  }, [editing]);

  // 폼의 저장: 실패하면 오류를 그대로 던져 폼이 안내를 보여 줌 (입력은 그대로)
  async function save(values, { onRetry } = {}) {
    const event = normalizeEvent(values);
    const saved = editing.id
      ? await myEvents.update(editing.id, event)
      : await myEvents.create(event, { onRetry });
    notify(editing.id ? `'${saved.name}' 행사를 수정했어요.` : `'${saved.name}' 행사를 등록했어요.`);
    setPeriod(isPast(saved, today) ? 'past' : 'upcoming');
    setEditing(null);
    focusTarget.current = saved.id;
  }

  function cancel() {
    focusTarget.current = editing.id || 'new';
    setEditing(null);
  }

  // 실패 안내의 '내 행사 보기': 폼을 닫고 목록을 새로 받음 (먼저 보낸 등록이 저장됐을 수 있음)
  function showList() {
    setEditing(null);
    focusTarget.current = 'new';
    reload();
  }

  async function remove(event) {
    setRemovingId(event.id);
    try {
      await myEvents.remove(event.id);
      notify(`'${event.name}' 행사를 삭제했어요.`);
      setConfirmId(null);
      focusTarget.current = 'new';
    } catch (err) {
      const notice = noticeFor(err);
      notify(`${notice.title} ${notice.body}`.trim());
    } finally {
      setRemovingId(null);
    }
  }

  return (
    <section className="panel events-tab" aria-labelledby="events-title">
      <div className="events-head">
        <div>
          <h2 id="events-title">
            내 행사 <em>{events.length}</em>
          </h2>
          <p>행사를 등록해 두면 행사 조건에 맞는 아티스트를 추천받을 수 있어요.</p>
        </div>
        {!editing && (
          <button
            ref={newButtonRef}
            type="button"
            className="primary"
            onClick={() => setEditing({ id: null })}
          >
            + 새 행사 등록
          </button>
        )}
      </div>

      {editing && (
        <EventForm
          key={editing.id || 'new'}
          initial={editingEvent ? toFormValues(editingEvent) : EMPTY_EVENT}
          isEdit={Boolean(editing.id)}
          serverMode={mode === 'server'}
          onSave={save}
          onCancel={cancel}
          onShowList={showList}
        />
      )}

      {!editing && status === 'loading' && (
        <p className="events-loading" role="status">
          <SoundWave /> 내 행사를 불러오는 중…
        </p>
      )}

      {!editing && status === 'error' && (
        <div className="events-error" role="alert">
          <strong>내 행사를 불러오지 못했어요.</strong>
          <p>{noticeFor(error).body || '잠시 뒤 다시 시도해 주세요.'}</p>
          <button type="button" className="secondary" onClick={reload}>
            다시 시도
          </button>
        </div>
      )}

      {!editing && status !== 'loading' && status !== 'error' && (
        <>
          <div className="events-period" role="group" aria-label="보여 줄 행사">
            {[
              ['upcoming', '다가오는 행사', upcoming.length],
              ['past', '지난 행사', past.length],
            ].map(([id, label, count]) => (
              <button
                key={id}
                type="button"
                aria-pressed={period === id}
                onClick={() => setPeriod(id)}
              >
                {label} <em>{count}</em>
              </button>
            ))}
          </div>

          {list.length ? (
            <ul className="event-list">
              {list.map((event) => {
                const cancelled = isCancelled(event);
                const overnight = endsNextDay(event.startTime, event.endTime);
                const flags = flagLists(event.details);
                const texts = DETAIL_TEXTS.filter(([id]) => event.details?.[id]);
                const hasSite = event.notes || event.stagePlan || texts.length || flags.yes.length || flags.no.length;
                return (
                  <li
                    key={event.id}
                    className={`event-card ${period === 'past' ? 'is-past' : ''} ${cancelled ? 'is-cancelled' : ''}`}
                  >
                    <div className="event-card-top">
                      <h3 id={`event-card-${event.id}`} tabIndex={-1}>
                        {event.name}
                      </h3>
                      <div className="event-tags">
                        {cancelled && <Tag>취소됨</Tag>}
                        <Tag>{visibilityLabel(event.visibility)}</Tag>
                      </div>
                    </div>
                    <p className="event-when">
                      {dayTitle(event.date)} · {event.startTime}~{overnight ? '다음 날 ' : ''}
                      {event.endTime}
                    </p>
                    <dl className="event-facts">
                      <div>
                        <dt>장소</dt>
                        <dd>
                          {event.venue} ({event.region})
                          {event.address && <small className="event-address">{event.address}</small>}
                        </dd>
                      </div>
                      <div>
                        <dt>행사 종류</dt>
                        <dd>{event.eventType}</dd>
                      </div>
                      <div>
                        <dt>원하는 공연</dt>
                        <dd>
                          {event.artistType} · {event.performanceMinutes}분 공연
                        </dd>
                      </div>
                      <div>
                        <dt>희망 장르</dt>
                        <dd>{event.genres.join(', ')}</dd>
                      </div>
                      <div>
                        <dt>예산</dt>
                        <dd>{budgetText(event)}</dd>
                      </div>
                      <div>
                        <dt>예상 관객</dt>
                        <dd>{event.audience.toLocaleString()}명</dd>
                      </div>
                    </dl>
                    <p className="event-description">{event.description}</p>
                    {hasSite ? (
                      <dl className="event-facts">
                        {texts.map(([id, label]) => (
                          <div key={id}>
                            <dt>{label}</dt>
                            <dd>{event.details[id]}</dd>
                          </div>
                        ))}
                        {flags.yes.length > 0 && (
                          <div>
                            <dt>제공</dt>
                            <dd>{flags.yes.join(', ')}</dd>
                          </div>
                        )}
                        {flags.no.length > 0 && (
                          <div>
                            <dt>제공 안 함</dt>
                            <dd>{flags.no.join(', ')}</dd>
                          </div>
                        )}
                        {event.notes && (
                          <div>
                            <dt>기타 조건</dt>
                            <dd className="event-notes">{event.notes}</dd>
                          </div>
                        )}
                        {event.stagePlan && (
                          <div>
                            <dt>무대 도면</dt>
                            <dd>{event.stagePlan.name}</dd>
                          </div>
                        )}
                      </dl>
                    ) : null}

                    {confirmId === event.id ? (
                      // 삭제는 되돌릴 수 없어 한 번 더 확인
                      <div className="event-confirm" role="group" aria-label={`${event.name} 삭제 확인`}>
                        <span>이 행사를 삭제할까요? 삭제하면 되돌릴 수 없어요.</span>
                        <button
                          type="button"
                          className="danger-button"
                          onClick={() => remove(event)}
                          disabled={removingId === event.id}
                        >
                          {removingId === event.id ? (
                            <>
                              <SoundWave /> 삭제하는 중…
                            </>
                          ) : (
                            '삭제'
                          )}
                        </button>
                        <button
                          type="button"
                          className="text-button"
                          id={`event-confirm-cancel-${event.id}`}
                          disabled={removingId === event.id}
                          onClick={() => {
                            setConfirmId(null);
                            focusTarget.current = event.id;
                          }}
                        >
                          취소
                        </button>
                      </div>
                    ) : (
                      <div className="event-actions">
                        {!cancelled && (
                          <button
                            type="button"
                            className="text-button"
                            onClick={() => setEditing({ id: event.id })}
                            aria-label={`${event.name} 수정`}
                          >
                            수정
                          </button>
                        )}
                        <button
                          type="button"
                          className="text-button danger"
                          onClick={() => setConfirmId(event.id)}
                          aria-label={`${event.name} 삭제`}
                          disabled={removingId !== null}
                        >
                          삭제
                        </button>
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          ) : (
            <p className="events-empty muted">
              {period === 'upcoming'
                ? events.length
                  ? '다가오는 행사가 없어요. 새 행사를 등록해 보세요.'
                  : '아직 등록한 행사가 없어요. 첫 행사를 등록해 보세요.'
                : '지난 행사가 없어요.'}
            </p>
          )}
        </>
      )}
    </section>
  );
}

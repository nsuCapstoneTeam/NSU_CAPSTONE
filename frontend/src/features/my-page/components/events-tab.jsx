import { useEffect, useRef, useState } from 'react';
import Tag from '../../../components/common/tag.jsx';
import { dayTitle, toDateKey } from '../availability.js';
import {
  EMPTY_EVENT,
  VISIBILITIES,
  budgetText,
  isPast,
  normalizeEvent,
  sortEvents,
  toFormValues,
} from '../organizer-events.js';
import EventForm from './event-form.jsx';
import './events-tab.css';

// 마이페이지 '내 행사' 탭 (행사 관계자, NSU-56 / 요구사항 EVT-041·EVT-042)
// 행사 목록 + 등록·수정·삭제. 서버 API(NSU-54) 전까지는 이 브라우저에만 저장합니다.
// 등록한 행사로 아티스트 추천받기는 NSU-58에서 연결합니다.

const visibilityLabel = (value) => VISIBILITIES.find((v) => v.value === value)?.label ?? value;

export default function EventsTab({ events, setEvents, notify }) {
  // 열린 폼: null(닫힘) | { id: null }(새 행사) | { id: '...' }(수정)
  const [editing, setEditing] = useState(null);
  // 삭제를 한 번 더 확인 중인 행사 id
  const [confirmId, setConfirmId] = useState(null);
  // 폼을 닫은 뒤 포커스를 돌려줄 대상 id ('new' = 새 행사 등록 버튼)
  const [focusTarget, setFocusTarget] = useState(null);
  const newButtonRef = useRef(null);

  const today = toDateKey(new Date());
  const list = sortEvents(events);
  const editingEvent = editing?.id ? events.find((e) => e.id === editing.id) : null;

  // 폼을 닫거나 저장한 뒤 포커스를 알맞은 곳으로 (키보드 사용자가 길을 잃지 않게)
  useEffect(() => {
    if (!focusTarget) return;
    if (focusTarget === 'new') newButtonRef.current?.focus();
    else document.getElementById(`event-card-${focusTarget}`)?.focus();
    setFocusTarget(null);
  }, [focusTarget]);

  // 삭제 확인이 열리면 '취소'에 포커스 (실수로 바로 삭제되지 않게)
  useEffect(() => {
    if (confirmId) document.getElementById(`event-confirm-cancel-${confirmId}`)?.focus();
  }, [confirmId]);

  // 폼이 열리면 첫 입력칸으로
  useEffect(() => {
    if (editing) document.getElementById('event-name')?.focus();
  }, [editing]);

  function save(values) {
    const isEdit = Boolean(editing.id);
    const id = editing.id || crypto.randomUUID();
    const saved = { ...normalizeEvent(values), id };
    const next = isEdit ? events.map((e) => (e.id === id ? saved : e)) : [...events, saved];
    const ok = setEvents(next);
    notify(
      ok
        ? isEdit
          ? `'${saved.name}' 행사를 수정했어요.`
          : `'${saved.name}' 행사를 등록했어요.`
        : '화면에는 반영됐지만 브라우저 저장에 실패했어요.',
    );
    setEditing(null);
    setFocusTarget(id);
  }

  function cancel() {
    setFocusTarget(editing.id || 'new');
    setEditing(null);
  }

  function remove(event) {
    const ok = setEvents(events.filter((e) => e.id !== event.id));
    notify(ok ? `'${event.name}' 행사를 삭제했어요.` : '화면에는 반영됐지만 브라우저 저장에 실패했어요.');
    setConfirmId(null);
    setFocusTarget('new');
  }

  return (
    <section className="panel events-tab" aria-labelledby="events-title">
      <div className="events-head">
        <div>
          <h2 id="events-title">
            내 행사 <em>{events.length}</em>
          </h2>
          <p>행사를 등록해 두면 행사 조건에 맞는 아티스트를 추천받을 수 있어요. (추천 연결은 준비 중)</p>
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
          onSave={save}
          onCancel={cancel}
        />
      )}

      {!editing && (
        <>
          {list.length ? (
            <ul className="event-list">
              {list.map((event) => {
                const past = isPast(event, today);
                return (
                  <li key={event.id} className={`event-card ${past ? 'is-past' : ''}`}>
                    <div className="event-card-top">
                      <h3 id={`event-card-${event.id}`} tabIndex={-1}>
                        {event.name}
                      </h3>
                      <div className="event-tags">
                        {past && <Tag>지난 행사</Tag>}
                        <Tag>{visibilityLabel(event.visibility)}</Tag>
                      </div>
                    </div>
                    <p className="event-when">
                      {dayTitle(event.date)} · {event.startTime}~{event.endTime}
                    </p>
                    <dl className="event-facts">
                      <div>
                        <dt>장소</dt>
                        <dd>
                          {event.venue} ({event.region})
                        </dd>
                      </div>
                      <div>
                        <dt>행사 종류</dt>
                        <dd>{event.eventType}</dd>
                      </div>
                      <div>
                        <dt>원하는 공연</dt>
                        <dd>
                          {event.artistType} · {event.genres.join(', ')} · {event.performanceMinutes}분
                        </dd>
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

                    {confirmId === event.id ? (
                      // 삭제는 되돌릴 수 없어 한 번 더 확인
                      <div className="event-confirm" role="group" aria-label={`${event.name} 삭제 확인`}>
                        <span>이 행사를 삭제할까요?</span>
                        <button type="button" className="danger-button" onClick={() => remove(event)}>
                          삭제
                        </button>
                        <button
                          type="button"
                          className="text-button"
                          id={`event-confirm-cancel-${event.id}`}
                          onClick={() => {
                            setConfirmId(null);
                            setFocusTarget(event.id);
                          }}
                        >
                          취소
                        </button>
                      </div>
                    ) : (
                      <div className="event-actions">
                        <button
                          type="button"
                          className="text-button"
                          onClick={() => setEditing({ id: event.id })}
                          aria-label={`${event.name} 수정`}
                        >
                          수정
                        </button>
                        <button
                          type="button"
                          className="text-button danger"
                          onClick={() => setConfirmId(event.id)}
                          aria-label={`${event.name} 삭제`}
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
              아직 등록한 행사가 없어요. 첫 행사를 등록해 보세요.
            </p>
          )}
        </>
      )}
    </section>
  );
}

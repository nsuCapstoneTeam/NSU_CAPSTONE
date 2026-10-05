import { useEffect, useRef, useState } from 'react';
import {
  STATUSES,
  WEEKDAYS,
  addDays,
  dayTitle,
  findOverlap,
  freeRanges,
  fromDateKey,
  monthCells,
  sortSchedules,
  statusLabel,
  statusOf,
  toDateKey,
} from '../availability.js';
import WeekTimetable from './week-timetable.jsx';
import './availability-panel.css';

// 마이페이지 '공연 일정' 탭 (ART-029)
// 왼쪽: 월 달력 + 추가·수정 폼 / 오른쪽: 주간 시간표 + 선택한 날의 빈 시간·일정 목록
// 저장은 이 브라우저(localStorage)에만 합니다. 서버 연동 전 체험용입니다.

const EMPTY_FORM = { start: '18:00', end: '20:00', status: 'AVAILABLE' };
// 방향키로 달력 칸을 옮길 때 더할 날짜 수
const KEY_STEPS = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };

const pad = (n) => String(n).padStart(2, '0');

export default function AvailabilityPanel({ active, schedules, setSchedules, notify }) {
  const todayKey = toDateKey(new Date());
  // 선택한 날짜 ('YYYY-MM-DD'). 추가·수정 폼의 날짜이기도 합니다.
  const [selected, setSelected] = useState(todayKey);
  // 지금 보고 있는 달 (month는 0부터 시작)
  const [view, setView] = useState(() => {
    const d = new Date();
    return { year: d.getFullYear(), month: d.getMonth() };
  });
  const [form, setForm] = useState(EMPTY_FORM);
  // 수정 중인 일정 id (없으면 새 일정 추가)
  const [editingId, setEditingId] = useState(null);

  const gridRef = useRef(null);
  const startRef = useRef(null);
  const statusRef = useRef(null);
  const dayHeadingRef = useRef(null);
  // 방향키로 날짜를 옮긴 뒤에만 달력 칸으로 포커스를 옮기기 위한 표시
  const focusDayRef = useRef(false);

  useEffect(() => {
    if (!focusDayRef.current) return;
    focusDayRef.current = false;
    gridRef.current?.querySelector(`[data-date="${selected}"]`)?.focus();
  }, [selected, view]);

  // 수정을 시작하면 시작 시간 칸으로 포커스 이동
  useEffect(() => {
    if (editingId) startRef.current?.focus();
  }, [editingId]);

  // 날짜별로 묶은 일정 { 'YYYY-MM-DD': [일정…] }
  const byDate = {};
  for (const item of schedules) (byDate[item.date] ||= []).push(item);
  const dayItems = byDate[selected] || [];
  const free = freeRanges(dayItems);
  const cells = monthCells(view.year, view.month);
  const monthPrefix = `${view.year}-${pad(view.month + 1)}`;
  const monthCount = schedules.filter((s) => s.date.startsWith(monthPrefix)).length;

  // 날짜 선택 (다른 달 날짜면 그 달로 이동)
  function selectDate(key, { focus = false } = {}) {
    const d = fromDateKey(key);
    setSelected(key);
    setView({ year: d.getFullYear(), month: d.getMonth() });
    if (focus) focusDayRef.current = true;
  }

  // 이전·다음 달로 이동하고, 그 달 1일을 선택
  function changeMonth(delta) {
    const d = new Date(view.year, view.month + delta, 1);
    setView({ year: d.getFullYear(), month: d.getMonth() });
    setSelected(toDateKey(d));
  }

  function dayKeyDown(e, key) {
    const step = KEY_STEPS[e.key];
    if (!step) return;
    e.preventDefault();
    selectDate(addDays(key, step), { focus: true });
  }

  function resetForm() {
    setEditingId(null);
    setForm(EMPTY_FORM);
  }

  function startEdit(item) {
    selectDate(item.date);
    setForm({ start: item.start, end: item.end, status: statusOf(item) });
    setEditingId(item.id);
  }

  // 주간 시간표 빈 칸: 그 날짜·시간부터 2시간으로 폼을 채우고 상태 선택칸으로 이동
  // 수정 중이었다면 그 일정의 상태를 넘겨받지 않도록 기본값('가능')으로 되돌림
  function pickSlot(key, hour) {
    const wasEditing = editingId !== null;
    setEditingId(null);
    selectDate(key);
    setForm((prev) => ({
      ...(wasEditing ? EMPTY_FORM : prev),
      start: `${pad(hour)}:00`,
      end: hour >= 22 ? '23:59' : `${pad(hour + 2)}:00`,
    }));
    statusRef.current?.focus();
  }

  function remove(item) {
    if (editingId === item.id) resetForm();
    const saved = setSchedules(schedules.filter((s) => s.id !== item.id));
    notify(
      saved
        ? `${dayTitle(item.date)} ${item.start}–${item.end} 일정을 삭제했습니다.`
        : '일정이 화면에서는 삭제됐지만 브라우저 저장에 실패했습니다.',
    );
    // 누른 삭제 버튼이 사라지므로 그날 제목으로 포커스를 옮김
    dayHeadingRef.current?.focus();
  }

  // 일정 추가·수정: 시간 순서와 같은 날 겹침을 확인한 뒤 정렬해 저장
  function submit(e) {
    e.preventDefault();
    const candidate = { date: selected, ...form };
    if (candidate.start >= candidate.end) {
      notify('종료 시간은 시작 시간보다 늦어야 합니다.');
      return;
    }
    const clash = findOverlap(schedules, candidate, editingId);
    if (clash) {
      notify(
        `같은 날 ${clash.start}–${clash.end} ${statusLabel(statusOf(clash))} 일정과 시간이 겹칩니다.`,
      );
      return;
    }
    const next = editingId
      ? schedules.map((s) => (s.id === editingId ? { ...s, ...candidate } : s))
      : [...schedules, { ...candidate, id: crypto.randomUUID() }];
    const saved = setSchedules(sortSchedules(next));
    notify(
      saved
        ? editingId
          ? '일정을 수정했습니다.'
          : '공연 가능 일정을 저장했습니다.'
        : '일정이 화면에는 반영됐지만 브라우저 저장에 실패했습니다. 새로고침하면 사라질 수 있습니다.',
    );
    resetForm();
  }

  return (
    <section className="panel availability">
      <h2>공연 가능 일정</h2>
      <p>
        달력이나 주간 시간표에서 날짜를 고르면 그날 비어 있는 시간과 등록한 일정을 볼 수
        있어요. 저장한 일정은 내 체험 프로필에만 적용되고, 샘플 추천 후보의 일정과는
        별개입니다.
      </p>

      {/* 상태 설명 (색과 모양, 글자를 함께 사용) */}
      <ul
        className="availability-legend"
        aria-label="일정 상태 설명"
      >
        {STATUSES.map((s) => (
          <li key={s.value}>
            <span
              className={`cal-mark ${s.value}`}
              aria-hidden="true"
            />
            {s.label}
          </li>
        ))}
      </ul>

      <div className="availability-layout">
        {/* 왼쪽: 월 달력 + 추가·수정 폼 */}
        <div>
          <div className="cal-head">
            <button
              type="button"
              className="text-button"
              onClick={() => changeMonth(-1)}
              aria-label="이전 달"
            >
              ‹ 이전
            </button>
            <h3 aria-live="polite">
              {view.year}년 {view.month + 1}월<span> · 일정 {monthCount}건</span>
            </h3>
            <button
              type="button"
              className="text-button"
              onClick={() => changeMonth(1)}
              aria-label="다음 달"
            >
              다음 ›
            </button>
          </div>
          <button
            type="button"
            className="text-button cal-today"
            onClick={() => selectDate(todayKey)}
          >
            오늘로 이동
          </button>

          {/* 월 달력: 선택한 날짜 칸만 Tab으로 들어가고, 칸 사이는 방향키로 이동 */}
          <div
            className="cal-grid"
            role="group"
            aria-label={`${view.year}년 ${view.month + 1}월 달력, 방향키로 날짜 이동`}
            ref={gridRef}
          >
            {WEEKDAYS.map((w) => (
              <span
                key={w}
                className="cal-weekday"
                aria-hidden="true"
              >
                {w}
              </span>
            ))}
            {cells.map((cell) => {
              const items = byDate[cell.key] || [];
              const counts = STATUSES.map((s) => [
                s,
                items.filter((it) => statusOf(it) === s.value).length,
              ]).filter(([, n]) => n);
              const summary = counts.length
                ? counts.map(([s, n]) => `${s.label} ${n}건`).join(', ')
                : '일정 없음';
              const isSelected = cell.key === selected;
              const isToday = cell.key === todayKey;
              return (
                <button
                  key={cell.key}
                  type="button"
                  data-date={cell.key}
                  className={`cal-day${cell.inMonth ? '' : ' out'}${isToday ? ' today' : ''}`}
                  tabIndex={isSelected ? 0 : -1}
                  aria-pressed={isSelected}
                  aria-current={isToday ? 'date' : undefined}
                  aria-label={`${dayTitle(cell.key)}${isToday ? ' 오늘' : ''}, ${summary}`}
                  onClick={() => selectDate(cell.key)}
                  onKeyDown={(e) => dayKeyDown(e, cell.key)}
                >
                  <span className="cal-num">{cell.day}</span>
                  <span
                    className="cal-marks"
                    aria-hidden="true"
                  >
                    {items.slice(0, 3).map((it) => (
                      <span
                        key={it.id}
                        className={`cal-mark ${statusOf(it)}`}
                      />
                    ))}
                    {items.length > 3 && <span className="cal-more">+{items.length - 3}</span>}
                  </span>
                </button>
              );
            })}
          </div>

          {/* 일정 추가·수정 폼 */}
          <form
            className="stacked-form availability-form"
            onSubmit={submit}
          >
            <h3>{editingId ? '일정 수정' : '일정 추가'}</h3>
            <div className="form-pair">
              <label>
                날짜
                <input
                  type="date"
                  required
                  value={selected}
                  onChange={(e) => e.target.value && selectDate(e.target.value)}
                />
              </label>
              <label>
                상태
                <select
                  ref={statusRef}
                  value={form.status}
                  onChange={(e) => setForm({ ...form, status: e.target.value })}
                >
                  {STATUSES.map((s) => (
                    <option
                      key={s.value}
                      value={s.value}
                    >
                      {s.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <div className="form-pair">
              <label>
                시작
                <input
                  ref={startRef}
                  type="time"
                  required
                  value={form.start}
                  onChange={(e) => setForm({ ...form, start: e.target.value })}
                />
              </label>
              <label>
                종료
                <input
                  type="time"
                  required
                  value={form.end}
                  onChange={(e) => setForm({ ...form, end: e.target.value })}
                />
              </label>
            </div>
            <small>
              &apos;확정&apos;은 실제 서비스에서 섭외 확정 시 자동으로 바뀔 수 있어요. (팀 확정
              필요)
            </small>
            <div className="availability-buttons">
              <button
                className="secondary"
                type="submit"
              >
                {editingId ? '수정 저장' : '일정 추가'}
              </button>
              {editingId && (
                <button
                  className="text-button"
                  type="button"
                  onClick={resetForm}
                >
                  수정 취소
                </button>
              )}
            </div>
          </form>
        </div>

        {/* 오른쪽: 주간 시간표 + 선택한 날 */}
        <div>
          <WeekTimetable
            active={active}
            selected={selected}
            todayKey={todayKey}
            byDate={byDate}
            editingId={editingId}
            onSelectDate={selectDate}
            onPickSlot={pickSlot}
            onEdit={startEdit}
          />

          <div className="day-detail">
            <h3
              ref={dayHeadingRef}
              tabIndex={-1}
            >
              {dayTitle(selected)}
            </h3>
            <p className="free-time">
              <strong>비어 있는 시간</strong>{' '}
              {free.length
                ? free.map((r) => `${r.start}–${r.end}`).join(', ')
                : '없음 (하루가 모두 채워져 있어요)'}
            </p>

            <div className="schedule-list">
              {dayItems.map((it) => (
                <div
                  key={it.id}
                  className={editingId === it.id ? 'is-editing' : undefined}
                >
                  <p>
                    <span className={`status-chip ${statusOf(it)}`}>
                      {statusLabel(statusOf(it))}
                    </span>
                    <strong>
                      {it.start}–{it.end}
                    </strong>
                  </p>
                  <span className="schedule-actions">
                    <button
                      type="button"
                      className="text-button"
                      onClick={() => startEdit(it)}
                      aria-label={`${dayTitle(it.date)} ${it.start} 일정 수정`}
                    >
                      수정
                    </button>
                    <button
                      type="button"
                      className="text-button danger"
                      onClick={() => remove(it)}
                      aria-label={`${dayTitle(it.date)} ${it.start} 일정 삭제`}
                    >
                      삭제
                    </button>
                  </span>
                </div>
              ))}
              {!dayItems.length && <p className="muted">이 날 등록한 일정이 없어요.</p>}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

import { useRef, useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import {
  STATUSES,
  WEEKDAYS,
  addDays,
  buildWeekItems,
  dayTitle,
  findOverlap,
  fromDateKey,
  shortRange,
  sortSchedules,
  statusLabel,
  statusOf,
  toDateKey,
  weekDays,
} from '../availability.js';
import MonthCalendar from './month-calendar.jsx';
import WeekTimetable from './week-timetable.jsx';
import WeekAddForm from './week-add-form.jsx';
import './availability-panel.css';

// 마이페이지 '공연 일정' 메뉴 (ART-029, NSU-110)
// - 월간: 한 달 달력에 상태·시간 표시. 날짜를 누르면 그 주의 주간 보기로 이동
// - 주간: 왼쪽 '시간 추가' 폼(요일·시간대·All Day·상태·일정이름) + 오른쪽 주간 시간표
// 저장은 이 브라우저(localStorage)에만 합니다. 서버 연동 전 체험용입니다.

const EMPTY_FORM = {
  days: [],
  start: '19:00',
  end: '22:00',
  allDay: false,
  status: 'AVAILABLE',
  title: '',
};

const pad = (n) => String(n).padStart(2, '0');
// '2026-10-18' → '10월 18일'
const monthDay = (key) => {
  const d = fromDateKey(key);
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
};

export default function AvailabilityPanel({ schedules, setSchedules, notify }) {
  const todayKey = toDateKey(new Date());
  const [mode, setMode] = useState('week');
  // 주간 보기: 보고 있는 주에 속한 아무 날짜
  const [weekKey, setWeekKey] = useState(todayKey);
  // 월간 보기: 보고 있는 달(month는 0부터)과 키보드로 고른 날짜
  const [view, setView] = useState(() => {
    const d = new Date();
    return { year: d.getFullYear(), month: d.getMonth() };
  });
  const [focusKey, setFocusKey] = useState(todayKey);
  const [form, setForm] = useState(EMPTY_FORM);
  // 수정 중인 일정 id / 시간표에서 고른 일정 id
  const [editingId, setEditingId] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const firstFieldRef = useRef(null);

  // 날짜별로 묶은 일정 { 'YYYY-MM-DD': [일정…] }
  const byDate = {};
  for (const item of schedules) (byDate[item.date] ||= []).push(item);

  const week = weekDays(weekKey);
  // 시간 추가 폼 값으로 만든 미리 보기 (시간 순서가 맞을 때만)
  const preview =
    form.allDay || form.start < form.end ? buildWeekItems(week, form) : [];
  const selectedItem = schedules.find((s) => s.id === selectedId);

  // 보기 전환 (월간으로 가면 보던 주의 달을 보여 줌)
  function changeMode(next) {
    if (next === 'month') {
      const d = fromDateKey(weekKey);
      setView({ year: d.getFullYear(), month: d.getMonth() });
      setFocusKey(weekKey);
    }
    setMode(next);
  }

  // 월간 날짜 이동 (다른 달 날짜면 그 달로)
  function focusDate(key) {
    const d = fromDateKey(key);
    setFocusKey(key);
    setView({ year: d.getFullYear(), month: d.getMonth() });
  }

  function changeMonth(delta) {
    const d = new Date(view.year, view.month + delta, 1);
    setView({ year: d.getFullYear(), month: d.getMonth() });
    setFocusKey(toDateKey(d));
  }

  // 월간에서 날짜를 누르면: 그 주의 주간 보기로 가고, 그 요일을 골라 둠
  function openDay(key) {
    setWeekKey(key);
    setSelectedId(null);
    if (!editingId) setForm((prev) => ({ ...prev, days: [fromDateKey(key).getDay()] }));
    setMode('week');
  }

  function resetForm() {
    setEditingId(null);
    setForm(EMPTY_FORM);
  }

  // 위쪽 '일정 추가' 버튼: 주간 보기의 폼 첫 칸(요일)으로 이동
  function startAdd() {
    setMode('week');
    setSelectedId(null);
    if (editingId) resetForm();
    setTimeout(() => firstFieldRef.current?.focus(), 0);
  }

  // 시간표 빈 칸: 그 요일·시간부터 2시간으로 폼을 채움
  function pickSlot(day, hour) {
    if (editingId) setEditingId(null);
    setSelectedId(null);
    setForm((prev) => ({
      ...(editingId ? EMPTY_FORM : prev),
      days: [day],
      allDay: false,
      start: `${pad(hour)}:00`,
      end: `${pad(Math.min(24, hour + 2))}:00`,
    }));
  }

  function startEdit(item) {
    setEditingId(item.id);
    setForm({
      days: [fromDateKey(item.date).getDay()],
      start: item.allDay ? EMPTY_FORM.start : item.start,
      end: item.allDay ? EMPTY_FORM.end : item.end,
      allDay: Boolean(item.allDay),
      status: statusOf(item) === 'BOOKED' ? 'AVAILABLE' : statusOf(item),
      title: item.title || '',
    });
    firstFieldRef.current?.focus();
  }

  function remove(item) {
    if (editingId === item.id) resetForm();
    setSelectedId(null);
    const saved = setSchedules(schedules.filter((s) => s.id !== item.id));
    notify(
      saved
        ? `${dayTitle(item.date)} ${shortRange(item.start, item.end)} 일정을 삭제했습니다.`
        : '일정이 화면에서는 삭제됐지만 브라우저 저장에 실패했습니다.',
    );
  }

  // 추가·수정: 요일·시간 순서·같은 날 겹침을 확인한 뒤 정렬해 저장
  function submit(e) {
    e.preventDefault();
    if (!form.days.length) {
      notify('요일을 하나 이상 골라 주세요.');
      firstFieldRef.current?.focus();
      return;
    }
    if (!form.allDay && form.start >= form.end) {
      notify('끝 시간은 시작 시간보다 늦어야 합니다.');
      return;
    }
    const candidates = buildWeekItems(week, form);
    for (const c of candidates) {
      const clash = findOverlap(schedules, c, editingId);
      if (clash) {
        notify(
          `${dayTitle(c.date)} ${shortRange(clash.start, clash.end)} ${statusLabel(statusOf(clash))} 일정과 시간이 겹칩니다.`,
        );
        return;
      }
    }
    // 수정은 원래 일정을 바꾸고(일정이름·All Day를 지웠다면 함께 지움), 추가는 새 id로 넣음
    const next = editingId
      ? schedules.map((s) => {
          if (s.id !== editingId) return s;
          const rest = { ...s };
          delete rest.title;
          delete rest.allDay;
          return { ...rest, ...candidates[0] };
        })
      : [...schedules, ...candidates.map((c) => ({ ...c, id: crypto.randomUUID() }))];
    const saved = setSchedules(sortSchedules(next));
    const days = candidates.map((c) => WEEKDAYS[fromDateKey(c.date).getDay()]).join('·');
    notify(
      saved
        ? editingId
          ? '일정을 수정했습니다.'
          : `${days} ${shortRange(candidates[0].start, candidates[0].end)} ${statusLabel(form.status)} 일정을 추가했습니다.`
        : '일정이 화면에는 반영됐지만 브라우저 저장에 실패했습니다. 새로고침하면 사라질 수 있습니다.',
    );
    // 시간·상태는 남겨 두고 요일·일정이름만 비움 (같은 시간을 다른 요일에 이어서 넣기 쉽게)
    setEditingId(null);
    setForm((prev) => ({ ...prev, days: [], title: '' }));
  }

  return (
    <div className="availability">
      <header className="my-content-head">
        <div>
          <h1>공연 일정</h1>
          <p>
            {mode === 'week'
              ? '요일과 시간대를 골라 공연할 수 있는 시간을 추가하세요. 월간 달력에도 함께 반영돼요.'
              : '공연할 수 있는 날과 이미 잡힌 공연을 한눈에 봐요. 날짜를 누르면 그 주로 이동해요.'}
          </p>
        </div>
        <div className="sched-actions">
          <div
            className="view-toggle"
            role="group"
            aria-label="일정 보기"
          >
            <button
              type="button"
              aria-pressed={mode === 'month'}
              onClick={() => changeMode('month')}
            >
              <Icon
                name="calendar-month"
                size={18}
              />
              월간
            </button>
            <button
              type="button"
              aria-pressed={mode === 'week'}
              onClick={() => changeMode('week')}
            >
              <Icon
                name="calendar-week"
                size={18}
              />
              주간
            </button>
          </div>
          <button
            type="button"
            className="primary sched-add"
            onClick={startAdd}
          >
            <Icon
              name="plus"
              size={18}
            />
            일정 추가
          </button>
        </div>
      </header>

      {mode === 'month' ? (
        <MonthCalendar
          view={view}
          focusKey={focusKey}
          todayKey={todayKey}
          byDate={byDate}
          onFocusDate={focusDate}
          onOpenDay={openDay}
          onChangeMonth={changeMonth}
          onToday={() => focusDate(todayKey)}
        />
      ) : (
        <div className="week-layout">
          <WeekAddForm
            form={form}
            setForm={setForm}
            editing={editingId !== null}
            firstFieldRef={firstFieldRef}
            onSubmit={submit}
            onCancelEdit={resetForm}
          />

          <section
            className="panel week-card"
            aria-label="주간 시간표"
          >
            <div className="sched-nav">
              <button
                type="button"
                className="sched-nav-arrow"
                onClick={() => setWeekKey(addDays(weekKey, -7))}
                aria-label="이전 주"
              >
                <Icon
                  name="chevron-left"
                  size={18}
                />
              </button>
              <h2 aria-live="polite">
                {monthDay(week[0])} – {monthDay(week[6])}
              </h2>
              <button
                type="button"
                className="sched-nav-arrow"
                onClick={() => setWeekKey(addDays(weekKey, 7))}
                aria-label="다음 주"
              >
                <Icon
                  name="chevron-right"
                  size={18}
                />
              </button>
              <button
                type="button"
                className="sched-nav-today"
                onClick={() => setWeekKey(todayKey)}
              >
                이번 주
              </button>
            </div>

            <WeekTimetable
              week={week}
              todayKey={todayKey}
              byDate={byDate}
              preview={preview}
              selectedId={selectedId}
              editingId={editingId}
              onPickSlot={pickSlot}
              onSelectItem={(id) => setSelectedId(id === selectedId ? null : id)}
            />

            {/* 시간표에서 고른 일정: 수정·삭제 */}
            {selectedItem && (
              <div
                className="week-selected"
                role="status"
              >
                <span className={`status-chip ${statusOf(selectedItem)}`}>
                  {statusLabel(statusOf(selectedItem))}
                </span>
                <strong>
                  {dayTitle(selectedItem.date)}{' '}
                  {selectedItem.allDay ? 'All Day' : `${selectedItem.start}–${selectedItem.end}`}
                </strong>
                {selectedItem.title && <span>{selectedItem.title}</span>}
                <span className="week-selected-actions">
                  {statusOf(selectedItem) !== 'BOOKED' && (
                    <button
                      type="button"
                      className="text-button"
                      onClick={() => startEdit(selectedItem)}
                    >
                      수정
                    </button>
                  )}
                  <button
                    type="button"
                    className="text-button danger"
                    onClick={() => remove(selectedItem)}
                  >
                    삭제
                  </button>
                </span>
              </div>
            )}

            <div className="week-foot">
              <ul
                className="sched-legend"
                aria-label="일정 상태 설명"
              >
                {STATUSES.map((s) => (
                  <li key={s.value}>
                    <span
                      className={`sched-dot ${s.value}`}
                      aria-hidden="true"
                    />
                    {s.label}
                  </li>
                ))}
              </ul>
              <p>점선은 추가하기 전 미리 보기예요. 블록을 누르면 고치거나 지울 수 있어요.</p>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

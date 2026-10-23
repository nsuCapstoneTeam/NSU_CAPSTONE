import { useEffect, useRef } from 'react';
import Icon from '../../../components/common/icon.jsx';
import {
  STATUSES,
  WEEKDAYS,
  addDays,
  availableHours,
  dayTitle,
  monthCells,
  shortRange,
  statusLabel,
  statusOf,
} from '../availability.js';

// 월간 달력 (NSU-110): 날짜 칸마다 일정을 '상태 · 시간'으로 보여 줍니다.
// 날짜를 누르면 그 주의 주간 보기로 이동해 시간을 추가·수정합니다.
// 선택한 날짜 칸만 Tab으로 들어가고, 칸 사이는 방향키로 이동합니다.

// 방향키로 달력 칸을 옮길 때 더할 날짜 수
const KEY_STEPS = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };
// 칸 하나에 보여 줄 일정 수 (넘치면 +N)
const MAX_CHIPS = 3;

export default function MonthCalendar({
  view,
  focusKey,
  todayKey,
  byDate,
  onFocusDate,
  onOpenDay,
  onChangeMonth,
  onToday,
}) {
  const gridRef = useRef(null);
  // 방향키로 날짜를 옮긴 뒤에만 달력 칸으로 포커스를 옮기기 위한 표시
  const moveFocusRef = useRef(false);
  const cells = monthCells(view.year, view.month);
  const monthItems = cells.filter((c) => c.inMonth).flatMap((c) => byDate[c.key] || []);

  useEffect(() => {
    if (!moveFocusRef.current) return;
    moveFocusRef.current = false;
    gridRef.current?.querySelector(`[data-date="${focusKey}"]`)?.focus();
  }, [focusKey, view]);

  function dayKeyDown(e, key) {
    const step = KEY_STEPS[e.key];
    if (!step) return;
    e.preventDefault();
    moveFocusRef.current = true;
    onFocusDate(addDays(key, step));
  }

  return (
    <section
      className="panel month-card"
      aria-label="월간 공연 일정"
    >
      <div className="sched-nav">
        <button
          type="button"
          className="sched-nav-arrow"
          onClick={() => onChangeMonth(-1)}
          aria-label="이전 달"
        >
          <Icon
            name="chevron-left"
            size={18}
          />
        </button>
        <h2 aria-live="polite">
          {view.year}년 {view.month + 1}월
        </h2>
        <button
          type="button"
          className="sched-nav-arrow"
          onClick={() => onChangeMonth(1)}
          aria-label="다음 달"
        >
          <Icon
            name="chevron-right"
            size={18}
          />
        </button>
        <button
          type="button"
          className="sched-nav-today"
          onClick={onToday}
        >
          오늘
        </button>
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
        <p className="month-total">이번 달 가능 {availableHours(monthItems)}시간</p>
      </div>

      <div
        className="month-grid"
        role="group"
        aria-label={`${view.year}년 ${view.month + 1}월 달력, 방향키로 날짜 이동, 날짜를 누르면 그 주로 이동`}
        ref={gridRef}
      >
        {WEEKDAYS.map((w, i) => (
          <span
            key={w}
            className={`month-weekday${i === 0 ? ' sunday' : ''}`}
            aria-hidden="true"
          >
            {w}
          </span>
        ))}
        {cells.map((cell, index) => {
          const items = byDate[cell.key] || [];
          const isToday = cell.key === todayKey;
          const summary = items.length
            ? items
                .map((it) => `${statusLabel(statusOf(it))} ${shortRange(it.start, it.end)}${it.title ? ` ${it.title}` : ''}`)
                .join(', ')
            : '일정 없음';
          return (
            <button
              key={cell.key}
              type="button"
              data-date={cell.key}
              className={`month-day${cell.inMonth ? '' : ' out'}${isToday ? ' today' : ''}${index % 7 === 0 ? ' sunday' : ''}`}
              tabIndex={cell.key === focusKey ? 0 : -1}
              aria-current={isToday ? 'date' : undefined}
              aria-label={`${dayTitle(cell.key)}${isToday ? ' 오늘' : ''}, ${summary}`}
              onClick={() => onOpenDay(cell.key)}
              onKeyDown={(e) => dayKeyDown(e, cell.key)}
            >
              <span className="month-num">{cell.day}</span>
              {items.slice(0, MAX_CHIPS).map((it) => (
                <span
                  key={it.id}
                  className={`month-chip ${statusOf(it)}`}
                  aria-hidden="true"
                >
                  {statusLabel(statusOf(it))} · {statusOf(it) === 'BOOKED' && it.title ? it.title : shortRange(it.start, it.end)}
                </span>
              ))}
              {items.length > MAX_CHIPS && (
                <span
                  className="month-more"
                  aria-hidden="true"
                >
                  +{items.length - MAX_CHIPS}
                </span>
              )}
            </button>
          );
        })}
      </div>
    </section>
  );
}

import { useEffect, useRef } from 'react';
import {
  WEEKDAYS,
  dayTitle,
  fromDateKey,
  statusLabel,
  statusOf,
  toMinutes,
  weekDays,
} from '../availability.js';

// 주간 시간표: 선택한 날짜가 속한 주(일~토)를 시간 줄로 보여 줍니다.
// - 요일 머리를 누르면 그 날짜 선택
// - 빈 칸을 누르면 그 날짜·시간으로 추가 폼을 채움 (마우스용 지름길, 키보드는 아래 폼 사용)
// - 일정 블록을 누르면 수정

const HOUR_HEIGHT = 32; // 1시간 줄 높이(px)
const FIRST_VISIBLE_HOUR = 8; // 처음 보이는 시간 (오전 8시)
const HOURS = Array.from({ length: 24 }, (_, h) => h);

export default function WeekTimetable({
  active,
  selected,
  todayKey,
  byDate,
  editingId,
  onSelectDate,
  onPickSlot,
  onEdit,
}) {
  const days = weekDays(selected);
  const scrollRef = useRef(null);
  const scrolledRef = useRef(false);

  // 탭이 처음 보일 때 한 번만 오전 8시 위치로 스크롤 (숨겨진 동안에는 스크롤이 적용되지 않음)
  useEffect(() => {
    if (!active || scrolledRef.current || !scrollRef.current) return;
    scrollRef.current.scrollTop = FIRST_VISIBLE_HOUR * HOUR_HEIGHT;
    scrolledRef.current = true;
  }, [active]);

  // 빈 칸 클릭 위치 → 몇 시인지 계산 (일정 블록을 누른 경우는 제외)
  function pick(e, key) {
    if (e.target !== e.currentTarget) return;
    const top = e.currentTarget.getBoundingClientRect().top;
    const hour = Math.min(23, Math.max(0, Math.floor((e.clientY - top) / HOUR_HEIGHT)));
    onPickSlot(key, hour);
  }

  const first = fromDateKey(days[0]);
  const last = fromDateKey(days[6]);

  return (
    <div className="week">
      <p className="week-title">
        {first.getMonth() + 1}월 {first.getDate()}일 ~ {last.getMonth() + 1}월 {last.getDate()}일
        주간 · 빈 칸을 누르면 그 시간으로 일정을 추가해요
      </p>

      {/* 요일 머리 */}
      <div className="week-head">
        <span aria-hidden="true" />
        {days.map((key, i) => (
          <button
            key={key}
            type="button"
            className={`week-day${key === todayKey ? ' today' : ''}`}
            aria-pressed={key === selected}
            aria-label={`${dayTitle(key)}${key === todayKey ? ' 오늘' : ''} 선택`}
            onClick={() => onSelectDate(key)}
          >
            <span>{WEEKDAYS[i]}</span>
            <strong>{fromDateKey(key).getDate()}</strong>
          </button>
        ))}
      </div>

      {/* 시간 줄 (이 안에서만 세로 스크롤) */}
      <div
        className="week-scroll"
        ref={scrollRef}
      >
        <div
          className="week-body"
          style={{ '--hour': `${HOUR_HEIGHT}px` }}
        >
          <div
            className="week-hours"
            aria-hidden="true"
          >
            {HOURS.map((h) => (
              <span key={h}>{h}시</span>
            ))}
          </div>
          {days.map((key) => (
            <div
              key={key}
              className={`week-col${key === selected ? ' selected' : ''}`}
              onClick={(e) => pick(e, key)}
            >
              {(byDate[key] || []).map((it) => {
                const start = toMinutes(it.start);
                const height = ((toMinutes(it.end) - start) / 60) * HOUR_HEIGHT;
                const label = statusLabel(statusOf(it));
                return (
                  <button
                    key={it.id}
                    type="button"
                    className={`week-block ${statusOf(it)}${editingId === it.id ? ' is-editing' : ''}`}
                    style={{ top: `${(start / 60) * HOUR_HEIGHT}px`, height: `${height}px` }}
                    aria-label={`${dayTitle(key)} ${it.start}–${it.end} ${label} 일정 수정`}
                    title={`${it.start}–${it.end} ${label}`}
                    onClick={() => onEdit(it)}
                  >
                    <strong>{label}</strong>
                    {height >= 44 && (
                      <span>
                        {it.start}–{it.end}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

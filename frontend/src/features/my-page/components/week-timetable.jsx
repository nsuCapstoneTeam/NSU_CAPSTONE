import { useEffect, useRef } from 'react';
import {
  WEEKDAYS,
  dayTitle,
  fromDateKey,
  shortRange,
  statusLabel,
  statusOf,
  toMinutes,
} from '../availability.js';

// 주간 시간표 (NSU-110): 한 주(일~토)를 시간 줄로 보여 줍니다.
// - 등록한 일정은 상태 색 블록, '시간 추가' 폼에서 고른 시간은 점선 블록(추가 전 미리 보기)
// - 빈 칸을 누르면 그 요일·시간으로 폼을 채움 (마우스용 지름길, 키보드는 왼쪽 폼 사용)
// - 일정 블록을 누르면 아래에 그 일정의 수정·삭제 버튼이 나타남

const HOUR_HEIGHT = 40; // 1시간 줄 높이(px)
const FIRST_VISIBLE_HOUR = 12; // 처음 보이는 시간 (낮 12시, 공연이 많은 오후·저녁부터)
const HOURS = Array.from({ length: 24 }, (_, h) => h);

// 'HH:MM' → 시간표 위쪽에서의 거리(px)
const toTop = (time) => (toMinutes(time) / 60) * HOUR_HEIGHT;

// week: 7일 'YYYY-MM-DD', byDate: 날짜별 일정, preview: 추가 전 미리 보기 일정 목록
// selectedId: 고른 일정 id, editingId: 수정 중인 일정 id (미리 보기로 대신 보여 주므로 흐리게)
export default function WeekTimetable({
  active,
  week,
  todayKey,
  byDate,
  preview,
  selectedId,
  editingId,
  onPickSlot,
  onSelectItem,
}) {
  const scrollRef = useRef(null);
  const scrolledRef = useRef(false);

  // 처음 보일 때 한 번만 낮 12시 위치로 스크롤 (숨겨진 동안에는 스크롤이 적용되지 않음)
  useEffect(() => {
    if (!active || scrolledRef.current || !scrollRef.current) return;
    scrollRef.current.scrollTop = FIRST_VISIBLE_HOUR * HOUR_HEIGHT;
    scrolledRef.current = true;
  }, [active]);

  // 빈 칸 클릭 위치 → 몇 시인지 계산 (일정 블록을 누른 경우는 제외)
  function pick(e, dayIndex) {
    if (e.target !== e.currentTarget) return;
    const top = e.currentTarget.getBoundingClientRect().top;
    const hour = Math.min(23, Math.max(0, Math.floor((e.clientY - top) / HOUR_HEIGHT)));
    onPickSlot(dayIndex, hour);
  }

  return (
    <div className="week">
      {/* 요일 머리 */}
      <div
        className="week-head"
        aria-hidden="true"
      >
        <span />
        {week.map((key, i) => (
          <span
            key={key}
            className={`week-day${key === todayKey ? ' today' : ''}${i === 0 ? ' sunday' : ''}`}
          >
            <span>{WEEKDAYS[i]}</span>
            <strong>{fromDateKey(key).getDate()}</strong>
          </span>
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
              <span key={h}>{String(h).padStart(2, '0')}:00</span>
            ))}
          </div>
          {week.map((key, dayIndex) => (
            <div
              key={key}
              className={`week-col${key === todayKey ? ' today' : ''}`}
              onClick={(e) => pick(e, dayIndex)}
            >
              {(byDate[key] || []).map((it) => {
                const status = statusOf(it);
                const height = toTop(it.end) - toTop(it.start);
                const label = statusLabel(status);
                return (
                  <button
                    key={it.id}
                    type="button"
                    className={`week-block ${status}${selectedId === it.id ? ' is-selected' : ''}${editingId === it.id ? ' is-editing' : ''}`}
                    style={{ top: `${toTop(it.start)}px`, height: `${height}px` }}
                    aria-pressed={selectedId === it.id}
                    aria-label={`${dayTitle(key)} ${shortRange(it.start, it.end)} ${label}${it.title ? ` ${it.title}` : ''} 일정 선택`}
                    onClick={() => onSelectItem(it.id)}
                  >
                    <strong>{it.allDay ? 'All Day' : shortRange(it.start, it.end)}</strong>
                    {height >= 36 && <span>{it.title || label}</span>}
                  </button>
                );
              })}
              {/* 추가 전 미리 보기 (점선) */}
              {preview
                .filter((p) => p.date === key)
                .map((p) => (
                  <span
                    key={`preview-${p.date}`}
                    className={`week-block is-preview ${p.status}`}
                    style={{ top: `${toTop(p.start)}px`, height: `${toTop(p.end) - toTop(p.start)}px` }}
                    aria-hidden="true"
                  >
                    <strong>{p.allDay ? 'All Day' : shortRange(p.start, p.end)}</strong>
                    <span>{p.title || statusLabel(p.status)}</span>
                  </span>
                ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

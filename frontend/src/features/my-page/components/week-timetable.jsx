import {
  VIEW_START,
  WEEKDAYS,
  clipToView,
  dayTitle,
  fromDateKey,
  shortRange,
  statusLabel,
  statusOf,
  toMinutes,
} from '../availability.js';

// 주간 시간표 (NSU-110): 한 주(일~토)를 오전 8시 ~ 밤 12시 시간 줄로 보여 줍니다.
// - 등록한 일정은 상태 색 블록, '시간 추가' 폼에서 고른 시간은 점선 블록(추가 전 미리 보기)
// - 빈 칸을 누르면 그 요일·시간으로 폼을 채움 (마우스용 지름길, 키보드는 왼쪽 폼 사용)
// - 일정 블록을 누르면 아래에 그 일정의 수정·삭제 버튼이 나타남

const HOUR_HEIGHT = 60; // 1시간 줄 높이(px). 넉넉하게 보이도록 크게 (페이지를 스크롤해서 봄)
const FIRST_HOUR = toMinutes(VIEW_START) / 60; // 8시
// 시간 줄 이름: 08:00 ~ 24:00 (마지막 24:00은 맨 아래 선)
const HOUR_LABELS = Array.from({ length: 25 - FIRST_HOUR }, (_, i) => FIRST_HOUR + i);
const EARLY_HEIGHT = 28; // 오전 8시 전에만 있는 일정을 맨 위에 작게 보여 줄 높이(px)

// 일정 → 시간표 안 위치 { top, height }(px)
// 오전 8시 전에만 있는 일정은 맨 위에 작게, 같은 날 여러 개면 earlyIndex 순서대로 아래로 쌓아 겹치지 않게 함
function blockStyle(start, end, earlyIndex = 0) {
  const range = clipToView(start, end);
  if (!range) return { top: `${earlyIndex * (EARLY_HEIGHT + 3)}px`, height: `${EARLY_HEIGHT}px` };
  const top = ((range[0] - FIRST_HOUR * 60) / 60) * HOUR_HEIGHT;
  const height = ((range[1] - range[0]) / 60) * HOUR_HEIGHT;
  return { top: `${top}px`, height: `${height}px` };
}

// week: 7일 'YYYY-MM-DD', byDate: 날짜별 일정, preview: 추가 전 미리 보기 일정 목록
// selectedId: 고른 일정 id, editingId: 수정 중인 일정 id (미리 보기로 대신 보여 주므로 흐리게)
export default function WeekTimetable({
  week,
  todayKey,
  byDate,
  preview,
  selectedId,
  editingId,
  onPickSlot,
  onSelectItem,
}) {
  // 빈 칸 클릭 위치 → 몇 시인지 계산 (일정 블록을 누른 경우는 제외)
  function pick(e, dayIndex) {
    if (e.target !== e.currentTarget) return;
    const top = e.currentTarget.getBoundingClientRect().top;
    const hour = Math.min(
      23,
      FIRST_HOUR + Math.max(0, Math.floor((e.clientY - top) / HOUR_HEIGHT)),
    );
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

      {/* 시간 줄 (오전 8시 ~ 밤 12시) */}
      <div className="week-scroll">
        <div
          className="week-body"
          style={{ '--hour': `${HOUR_HEIGHT}px` }}
        >
          <div
            className="week-hours"
            aria-hidden="true"
          >
            {HOUR_LABELS.map((h) => (
              <span key={h}>{String(h).padStart(2, '0')}:00</span>
            ))}
          </div>
          {week.map((key, dayIndex) => (
            <div
              key={key}
              className={`week-col${key === todayKey ? ' today' : ''}`}
              onClick={(e) => pick(e, dayIndex)}
            >
              {(byDate[key] || []).map((it, index, items) => {
                const status = statusOf(it);
                // 이 일정보다 앞에 있는 '8시 전에만 있는 일정' 수 (쌓는 순서)
                const earlyIndex = items
                  .slice(0, index)
                  .filter((other) => !clipToView(other.start, other.end)).length;
                const style = blockStyle(it.start, it.end, earlyIndex);
                const height = parseFloat(style.height);
                const label = statusLabel(status);
                return (
                  <button
                    key={it.id}
                    type="button"
                    className={`week-block ${status}${selectedId === it.id ? ' is-selected' : ''}${editingId === it.id ? ' is-editing' : ''}`}
                    style={style}
                    aria-pressed={selectedId === it.id}
                    aria-label={`${dayTitle(key)} ${shortRange(it.start, it.end)} ${label}${it.title ? ` ${it.title}` : ''} 일정 선택`}
                    onClick={() => onSelectItem(it.id)}
                  >
                    <strong>{it.allDay ? 'All Day' : shortRange(it.start, it.end)}</strong>
                    {height >= 44 && <span>{it.title || label}</span>}
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
                    style={blockStyle(p.start, p.end)}
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

import { useId } from 'react';
import Icon from '../../../components/common/icon.jsx';
import { PICKABLE_STATUSES, WEEKDAYS, statusLabel, timeChoices } from '../availability.js';

// 주간 보기 왼쪽 '시간 추가' 카드 (NSU-110)
// 요일(여러 개) · 시간대 · All Day · 상태 · 일정이름(선택)을 고르고 추가합니다.
// 고르는 동안 오른쪽 시간표에 점선으로 미리 보입니다.

// 추가 버튼 문구: '목·금 19:00–22:00 추가하기', '월 All Day 불가로 추가하기'
function submitLabel(form, editing) {
  if (editing) return '수정 저장';
  if (!form.days.length) return '추가하기';
  const days = [...form.days]
    .sort((a, b) => a - b)
    .map((d) => WEEKDAYS[d])
    .join('·');
  const range = form.allDay ? 'All Day' : `${form.start}–${form.end}`;
  const status = form.status === 'AVAILABLE' ? '' : ` ${statusLabel(form.status)}로`;
  return `${days} ${range}${status} 추가하기`;
}

// form: 폼 값, setForm: 폼 값 바꾸기, editing: 수정 중인지, firstFieldRef: 첫 칸(일요일 버튼)
export default function WeekAddForm({
  form,
  setForm,
  editing,
  firstFieldRef,
  onSubmit,
  onCancelEdit,
}) {
  // 폼 값 일부만 바꿈 (예: update({ allDay: true }))
  const update = (patch) => setForm((prev) => ({ ...prev, ...patch }));
  const timeLabelId = useId();

  // 요일 버튼: 새로 추가할 때는 여러 개, 수정할 때는 하나만 고름
  function toggleDay(day) {
    if (editing) {
      update({ days: [day] });
      return;
    }
    update({
      days: form.days.includes(day) ? form.days.filter((d) => d !== day) : [...form.days, day],
    });
  }

  return (
    <section
      className="panel week-add"
      aria-label="공연 가능 시간 추가"
    >
      <form
        className="week-form"
        onSubmit={onSubmit}
      >
        <h2>{editing ? '일정 수정' : '시간 추가'}</h2>

        <fieldset className="week-days">
          <legend>요일 {editing ? '(하나만)' : '(여러 개 고를 수 있어요)'}</legend>
          <div>
            {WEEKDAYS.map((name, day) => (
              <button
                key={name}
                ref={day === 0 ? firstFieldRef : undefined}
                type="button"
                className={day === 0 ? 'sunday' : undefined}
                aria-pressed={form.days.includes(day)}
                onClick={() => toggleDay(day)}
              >
                {name}
              </button>
            ))}
          </div>
        </fieldset>

        {/* 시간대: 제목 옆에 All Day 스위치가 있어 fieldset 대신 이름 붙은 묶음으로 만듦 */}
        <div
          className="week-time"
          role="group"
          aria-labelledby={timeLabelId}
        >
          <div className="week-time-head">
            <span id={timeLabelId}>시간대</span>
            <button
              type="button"
              role="switch"
              aria-checked={form.allDay}
              className="all-day-switch"
              onClick={() => update({ allDay: !form.allDay })}
            >
              <span aria-hidden="true" />
              All Day
            </button>
          </div>
          <div className="week-time-row">
            <label>
              <span className="sr-only">시작 시간</span>
              <select
                value={form.start}
                disabled={form.allDay}
                onChange={(e) => update({ start: e.target.value })}
              >
                {timeChoices('start', form.start).map((t) => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </label>
            <span aria-hidden="true">–</span>
            <label>
              <span className="sr-only">끝 시간</span>
              <select
                value={form.end}
                disabled={form.allDay}
                onChange={(e) => update({ end: e.target.value })}
              >
                {timeChoices('end', form.end).map((t) => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </label>
          </div>
        </div>

        <fieldset className="week-status">
          <legend>상태</legend>
          <div>
            {PICKABLE_STATUSES.map((s) => (
              <label
                key={s.value}
                className={s.value}
              >
                <input
                  type="radio"
                  name="week-status"
                  value={s.value}
                  checked={form.status === s.value}
                  onChange={() => update({ status: s.value })}
                />
                <span
                  className={`sched-dot ${s.value}`}
                  aria-hidden="true"
                />
                {s.label}
              </label>
            ))}
          </div>
          <small>확정은 섭외가 성사되면 자동으로 표시돼요.</small>
        </fieldset>

        <label className="week-title-field">
          <span>
            일정이름 <em>선택</em>
          </span>
          <input
            type="text"
            maxLength={30}
            value={form.title}
            placeholder="예: 버스킹, 개인 레슨, 리허설"
            onChange={(e) => update({ title: e.target.value })}
          />
        </label>

        <button
          type="submit"
          className="primary week-submit"
        >
          {!editing && (
            <Icon
              name="plus"
              size={18}
            />
          )}
          {submitLabel(form, editing)}
        </button>
        {editing && (
          <button
            type="button"
            className="text-button"
            onClick={onCancelEdit}
          >
            수정 취소
          </button>
        )}
      </form>
    </section>
  );
}

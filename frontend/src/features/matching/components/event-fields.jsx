import Select from '../../../components/common/select.jsx';
import { REGIONS } from '../matching.js';

// 참고 곡으로 찾기에서 쓰는 행사 조건 칸 (NSU-111)
// 일정·지역·예산·공연 시간으로 섭외할 수 있는 아티스트만 남깁니다. (음악 스타일 칸은 참고 곡이 대신함)
// form: 행사 조건 값, change(name, value): 칸 하나 바꾸기, withTitle: 행사명 칸 표시
export default function EventFields({ form, change, withTitle = true }) {
  return (
    <>
      {withTitle && (
        <label>
          행사명
          <input
            required
            maxLength={80}
            value={form.title}
            onChange={(e) => change('title', e.target.value)}
          />
        </label>
      )}
      <div className="form-pair">
        <label>
          공연 날짜
          <input
            type="date"
            required
            value={form.date}
            onChange={(e) => change('date', e.target.value)}
          />
        </label>
        <label>
          시작 시간
          <input
            type="time"
            required
            value={form.start}
            onChange={(e) => change('start', e.target.value)}
          />
        </label>
      </div>
      <div className="form-pair">
        <Select
          label="지역"
          value={form.region}
          options={REGIONS}
          onChange={(v) => change('region', v)}
        />
        <label>
          공연 시간 (분)
          <input
            type="number"
            min="1"
            max="1440"
            required
            value={form.minutes}
            onChange={(e) => change('minutes', e.target.value)}
          />
        </label>
      </div>
      <label>
        최대 예산 (만 원)
        <input
          type="number"
          min="1"
          max="100000"
          required
          value={form.budget}
          onChange={(e) => change('budget', e.target.value)}
        />
      </label>
    </>
  );
}

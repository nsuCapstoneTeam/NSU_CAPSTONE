// 라벨이 붙은 선택 상자. options 문자열 목록을 보여 주고, 고른 값을 onChange로 전달합니다.
export default function Select({ label, value, options, onChange }) {
  return (
    <label>
      {label}
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        {options.map((x) => (
          <option key={x}>{x}</option>
        ))}
      </select>
    </label>
  );
}

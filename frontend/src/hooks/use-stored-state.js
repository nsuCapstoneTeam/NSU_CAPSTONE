import { useState } from 'react';
import { localDemoStore } from '../storage/local-demo-store.js';
// useState처럼 쓰되, 값이 바뀔 때마다 체험 저장소(localStorage)에도 저장하는 훅.
// 반환값: [값, 값 변경 함수, 저장 실패 여부]
// 값 변경 함수는 브라우저 저장에 성공하면 true, 실패하면 false를 돌려줌
export default function useStoredState(
  key,
  initialValue,
  validate = () => true,
) {
  // 처음 한 번만 저장소에서 읽고, 없거나 형식이 맞지 않으면 initialValue 사용
  const [value, setValue] = useState(() => {
    return localDemoStore.read(key, initialValue, validate);
  });
  const [storageFailed, setStorageFailed] = useState(false);
  // 함수(이전 값 → 새 값) 또는 새 값을 받아 화면 상태와 저장소를 함께 갱신
  function update(next) {
    const resolved = typeof next === 'function' ? next(value) : next;
    setValue(resolved);
    const saved = localDemoStore.write(key, resolved);
    setStorageFailed(!saved);
    return saved;
  }
  return [value, update, storageFailed];
}

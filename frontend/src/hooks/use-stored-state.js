import { useState } from 'react';
import { localDemoStore } from '../storage/local-demo-store.js';
export default function useStoredState(
  key,
  initialValue,
  validate = () => true,
) {
  const [value, setValue] = useState(() => {
    return localDemoStore.read(key, initialValue, validate);
  });
  const [storageFailed, setStorageFailed] = useState(false);
  function update(next) {
    const resolved = typeof next === 'function' ? next(value) : next;
    setValue(resolved);
    setStorageFailed(!localDemoStore.write(key, resolved));
  }
  return [value, update, storageFailed];
}

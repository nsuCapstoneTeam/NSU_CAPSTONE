import test from 'node:test';
import assert from 'node:assert/strict';
import { REGISTRATION_KEY, createRegistrationKey } from './registration-key.js';

const ID_A = '11111111-1111-4111-8111-111111111111';
const ID_B = '22222222-2222-4222-8222-222222222222';
function memoryStorage() {
  const data = new Map();
  return {
    data,
    getItem: (k) => (data.has(k) ? data.get(k) : null),
    setItem: (k, v) => data.set(k, String(v)),
    removeItem: (k) => data.delete(k),
  };
}

test('결과가 나올 때까지 같은 키를 쓰고, 새로고침 뒤에도 이어 쓴다', () => {
  const storage = memoryStorage();
  const ids = [ID_A, ID_B];
  const key = createRegistrationKey(() => storage, () => ids.shift());
  assert.equal(key.current(), ID_A);
  assert.equal(key.current(), ID_A);
  assert.equal(storage.data.get(REGISTRATION_KEY), ID_A);
  // 새로고침: 새 창구도 저장된 키를 씀
  const again = createRegistrationKey(() => storage, () => ID_B);
  assert.equal(again.current(), ID_A);
});

test('결과가 확정되면 키를 버리고 다음 등록은 새 키를 쓴다', () => {
  const storage = memoryStorage();
  const ids = [ID_A, ID_B];
  const key = createRegistrationKey(() => storage, () => ids.shift());
  key.current();
  key.discard();
  assert.equal(storage.data.has(REGISTRATION_KEY), false);
  assert.equal(key.current(), ID_B);
});

test('저장소를 못 써도 이 탭에서는 같은 키를 쓰고, 깨진 값은 새 키로 바꾼다', () => {
  const key = createRegistrationKey(() => {
    throw new Error('blocked');
  }, () => ID_A);
  assert.equal(key.current(), ID_A);
  assert.equal(key.current(), ID_A);
  const storage = memoryStorage();
  storage.setItem(REGISTRATION_KEY, 'not-a-uuid');
  assert.equal(createRegistrationKey(() => storage, () => ID_B).current(), ID_B);
});

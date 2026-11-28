// 행사 등록 키 (Idempotency-Key, 행사 API §5.3.1, NSU-125)
// 등록 화면을 열 때 키를 하나 만들고, 결과가 확정될 때까지(등록됨·이미 등록됨·삭제됨) 같은 키를 씁니다.
// 새로고침 뒤에도 같은 키를 쓰도록 결과가 나올 때까지 sessionStorage에 둡니다.
// (실행 테스트: node --test src/features/events/registration-key.test.js)
export const REGISTRATION_KEY = 'hm-event-registration-key-v1';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

// 결과가 확정돼 키를 버려야 하는 오류 코드 (그 밖의 실패는 같은 키로 다시 보냄)
export const SETTLED_CODES = new Set(['IDEMPOTENCY_KEY_REUSED', 'EVENT_NOT_FOUND']);

export function createRegistrationKey(getStorage, newId = () => crypto.randomUUID()) {
  const storage = () => {
    try {
      return getStorage();
    } catch {
      return null;
    }
  };
  let memory = null; // 저장소를 못 쓸 때도 이 탭에서는 같은 키를 쓰게
  const read = () => {
    try {
      return storage()?.getItem(REGISTRATION_KEY) ?? null;
    } catch {
      return null;
    }
  };
  return {
    // 지금 키. 없으면 새로 만들어 기억
    current() {
      const key = read();
      if (key && UUID.test(key)) {
        // 새로고침 뒤 되살린 키도 기억 (이후 저장소가 막혀도 같은 등록은 같은 키로)
        memory = key;
        return key;
      }
      if (memory) return memory;
      memory = String(newId()).toLowerCase();
      try {
        storage()?.setItem(REGISTRATION_KEY, memory);
      } catch {
        // 기억하지 못해도 이 탭에서는 memory로 같은 키를 씀
      }
      return memory;
    },
    // 결과가 확정되면 키를 버림 (다음 등록은 새 키)
    discard() {
      memory = null;
      try {
        storage()?.removeItem(REGISTRATION_KEY);
      } catch {
        // 지울 수 없으면 다음 current()가 저장된 키를 다시 씀
      }
    },
  };
}

export const registrationKey = createRegistrationKey(() => window.sessionStorage);

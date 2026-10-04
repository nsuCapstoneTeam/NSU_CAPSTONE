// 회원가입 화면에서 빠진 항목으로 이동
// focus가 false면 화면만 그 칸으로 옮기고 반짝임 (포커스는 안내 카드에 둘 때)
export function focusSignupField(id, { focus = true } = {}) {
  const el = document.getElementById(id);
  if (!el) return;
  el.scrollIntoView({ behavior: 'smooth', block: 'center' });
  if (focus) el.focus({ preventScroll: true });
  // 이동한 칸을 잠깐 반짝여서 어디인지 알 수 있게 (선택 버튼 묶음은 묶음 전체)
  const target = el.closest('.signup-choice-group') ?? el.closest('label') ?? el;
  target.classList.remove('signup-flash');
  void target.offsetWidth; // 연속으로 눌러도 다시 재생되도록
  target.classList.add('signup-flash');
  window.setTimeout(() => target.classList.remove('signup-flash'), 1400);
}

// 외부 계정 화면 소스 검사 (실행: node --test src/features/external-accounts/external-accounts-source.test.js)
// - 재생기를 넣지 않는다: 외부 링크 ADR 1번 (재생은 각 플랫폼 사이트에서 한다)
// - 플랫폼이 준 글자는 HTML이 아닌 글자로만 그린다: API 06 §6.4 불변식 9
// - Google 동의 화면은 같은 탭에서 연다: API 06 §3 (결합 쿠키와 돌아올 화면이 같은 탭이어야 한다)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const DIR = dirname(fileURLToPath(import.meta.url));
const sources = readdirSync(DIR)
  .filter((name) => /\.(js|jsx)$/.test(name) && !name.endsWith('.test.js'))
  .map((name) => ({ name, text: readFileSync(join(DIR, name), 'utf8') }));

const without = (pattern) => sources.filter(({ text }) => pattern.test(text)).map(({ name }) => name);

describe('외부 계정 화면 소스', () => {
  it('검사할 파일을 실제로 찾았다', () => {
    assert.ok(sources.length >= 8, `찾은 파일 ${sources.length}개`);
  });
  it('재생기(iframe, video, audio, embed)를 넣지 않는다', () => {
    assert.deepEqual(without(/<(iframe|video|audio|embed|object)\b/i), []);
  });
  it('플랫폼이 준 글자를 HTML로 그리지 않는다', () => {
    assert.deepEqual(without(/dangerouslySetInnerHTML|\.innerHTML\b|insertAdjacentHTML/), []);
  });
  it('새 창을 열지 않는다', () => {
    assert.deepEqual(without(/window\.open\s*\(/), []);
  });
});

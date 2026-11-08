import test from 'node:test';
import assert from 'node:assert/strict';
import { ROUTES, pageOf, pathOf, titleOf } from './routes.js';

test('화면 이름과 주소가 서로 바뀐다', () => {
  for (const [page, path] of Object.entries(ROUTES)) {
    assert.equal(pathOf(page), path);
    assert.equal(pageOf(path), page);
  }
  assert.equal(new Set(Object.values(ROUTES)).size, Object.keys(ROUTES).length);
});

test('주소 끝의 / · 대소문자 · 쿼리는 무시하고, 모르는 주소는 홈', () => {
  assert.equal(pageOf('/events/'), 'events');
  assert.equal(pageOf('/Match'), 'match');
  assert.equal(pageOf('/login/organizer?from=qr'), 'organizer-login');
  assert.equal(pageOf('//board'), 'board');
  assert.equal(pageOf('/없는-화면'), 'home');
  assert.equal(pageOf(''), 'home');
  assert.equal(pathOf('없는-화면'), '/');
});

test('객체가 물려받은 이름(toString 등)은 모르는 화면으로 본다', () => {
  for (const name of ['toString', 'constructor', 'hasOwnProperty', '__proto__']) {
    assert.equal(pathOf(name), '/', name);
    assert.equal(titleOf(name), 'CLAP SOUND', name);
  }
  assert.equal(pageOf('/toString'), 'home');
  assert.equal(pageOf('/constructor'), 'home');
});

test('가입 완료는 주소로 바로 열면 홈, 뒤로 가기로 돌아올 때는 그대로', () => {
  assert.equal(pageOf('/signup/complete', { onLoad: true }), 'home');
  assert.equal(pageOf('/signup/complete'), 'signup-complete');
  assert.equal(pageOf('/signup/artist', { onLoad: true }), 'artist-signup');
});

test('탭 제목', () => {
  assert.equal(titleOf('events'), '행사 찾기 · CLAP SOUND');
  assert.equal(titleOf('home'), 'CLAP SOUND');
});

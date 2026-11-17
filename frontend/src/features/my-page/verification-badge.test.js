// 확인 배지 규칙 테스트 (실행: node --test src/features/my-page/verification-badge.test.js)
// 외부 링크 ADR 원칙 테스트 8: 외부 계정만으로는 배지가 켜지지 않지만 목록에는 나온다.
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  NO_ITEMS_TITLE,
  badgeView,
  describeVerifications,
  itemsFromNames,
} from './verification-badge.js';

const YOUTUBE_CONTROL = 'YouTube 채널 제어권 확인';

describe('외부 계정만 있는 아티스트', () => {
  const view = badgeView(describeVerifications([{ type: 'EXTERNAL_ACCOUNT', provider: 'YOUTUBE' }]));

  it('배지는 회색이다', () => {
    assert.equal(view.isBlue, false);
  });
  it('목록에 "YouTube 채널 제어권 확인"이 나온다', () => {
    assert.deepEqual(view.listItems, [YOUTUBE_CONTROL]);
  });
  it('화면 낭독기가 읽는 이름에도 같은 항목이 들어 있다', () => {
    assert.ok(view.label.includes(YOUTUBE_CONTROL), view.label);
  });
  it('"아직 확인된 항목이 없어요"는 나오지 않는다', () => {
    assert.notEqual(view.title, NO_ITEMS_TITLE);
    assert.ok(!view.label.includes('아직 확인된 항목이 없는'), view.label);
  });
});

describe('확인된 항목이 하나도 없는 아티스트', () => {
  const view = badgeView(describeVerifications([]));

  it('회색이고 "아직 확인된 항목이 없어요"만 보인다', () => {
    assert.equal(view.isBlue, false);
    assert.deepEqual(view.listItems, []);
    assert.equal(view.title, NO_ITEMS_TITLE);
    assert.equal(view.label, '아직 확인된 항목이 없는 아티스트');
  });
});

describe('서버가 새로 추가한 확인 종류', () => {
  it('모르는 type은 목록에만 나오고 배지를 켜지 않는다', () => {
    const view = badgeView(describeVerifications([{ type: 'NEW_KIND', provider: null }]));
    assert.equal(view.isBlue, false);
    assert.deepEqual(view.listItems, ['확인된 항목']);
  });
});

describe('이름 목록만 넘기는 기존 화면(관심 아티스트, 추천 카드)', () => {
  it('목록의 이름이 있으면 지금처럼 파랗고 같은 이름이 목록에 나온다', () => {
    const view = badgeView(itemsFromNames(['작업물 권리 확인']));
    assert.equal(view.isBlue, true);
    assert.deepEqual(view.listItems, ['작업물 권리 확인']);
  });
  it('목록이 비었거나 없으면 회색이다', () => {
    assert.equal(badgeView(itemsFromNames([])).isBlue, false);
    assert.equal(badgeView(itemsFromNames(undefined)).isBlue, false);
  });
});

describe('실명 본인확인을 마친 아티스트', () => {
  it('배지가 파랗고, 외부 계정도 목록에 함께 나온다', () => {
    const view = badgeView(
      describeVerifications([
        { type: 'IDENTITY', provider: null },
        { type: 'EXTERNAL_ACCOUNT', provider: 'YOUTUBE' },
      ]),
    );
    assert.equal(view.isBlue, true);
    assert.deepEqual(view.listItems, ['실명 본인확인', YOUTUBE_CONTROL]);
    assert.equal(view.label, `확인된 아티스트: 실명 본인확인, ${YOUTUBE_CONTROL}`);
  });
});

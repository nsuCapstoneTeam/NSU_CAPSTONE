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

// 증빙 확인 기록 ADR 12번: 세 항목은 배지를 켜지 않고 목록에만 이름으로 보인다. 배지 기준은 NSU-145가 정한다.
describe('사업자등록 증빙 확인 기록만 있는 아티스트', () => {
  const view = badgeView(describeVerifications([{ type: 'BUSINESS_REGISTRATION', provider: null }]));

  it('배지는 회색이다', () => {
    assert.equal(view.isBlue, false);
  });
  it('목록에 "사업자등록 확인"이 나온다', () => {
    assert.deepEqual(view.listItems, ['사업자등록 확인']);
  });
  it('"아직 확인된 항목이 없어요"는 나오지 않는다', () => {
    assert.notEqual(view.title, NO_ITEMS_TITLE);
  });
});

describe('실연 참여·공식 활동 증빙 확인 기록', () => {
  it('두 항목은 서로 다른 이름으로 목록에 나오고 배지는 회색이다', () => {
    const view = badgeView(
      describeVerifications([
        { type: 'PERFORMANCE_PARTICIPATION', provider: null },
        { type: 'OFFICIAL_ACTIVITY', provider: null },
      ]),
    );
    assert.equal(view.isBlue, false);
    assert.deepEqual(view.listItems, ['실연 참여 확인', '공식 활동 확인']);
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

// 증빙 확인 기록 ADR 12번은 이름 목록으로 넘어오는 경로에도 같다: 색을 켜지 않는 항목은 목록에만 나온다.
describe('이름 목록 중 색을 켜지 않는 항목', () => {
  it('"사업자등록 확인"은 배지를 켜지 않고 목록에만 나온다', () => {
    const items = itemsFromNames(['사업자등록 확인']);
    assert.deepEqual(items.badgeItems, []);
    assert.deepEqual(items.listItems, ['사업자등록 확인']);
    assert.equal(badgeView(items).isBlue, false);
  });

  it('"실연 참여 확인"과 "공식 활동 확인"도 목록에만 나온다', () => {
    const items = itemsFromNames(['실연 참여 확인', '공식 활동 확인']);
    assert.deepEqual(items.badgeItems, []);
    assert.deepEqual(items.listItems, ['실연 참여 확인', '공식 활동 확인']);
  });

  it('외부 계정 이름(YouTube, 그 밖의 플랫폼)도 목록에만 나온다', () => {
    const names = [YOUTUBE_CONTROL, '외부 계정 제어권 확인'];
    const items = itemsFromNames(names);
    assert.deepEqual(items.badgeItems, []);
    assert.deepEqual(items.listItems, names);
  });

  it('Trust Profile 항목에서 뽑은 이름을 다시 넘겨도 색과 목록이 같다', () => {
    const verifications = [
      { type: 'IDENTITY', provider: null },
      { type: 'EXTERNAL_ACCOUNT', provider: 'YOUTUBE' },
      { type: 'COPYRIGHT', provider: null },
      { type: 'BUSINESS_REGISTRATION', provider: null },
      { type: 'NEW_KIND', provider: null },
    ];
    const described = describeVerifications(verifications);
    assert.deepEqual(itemsFromNames(described.listItems), described);
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

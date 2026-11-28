import { useEffect, useRef } from 'react';
import Icon from '../../../components/common/icon.jsx';
import Tag from '../../../components/common/tag.jsx';
import { budgetText } from '../../my-page/organizer-events.js';
import { dateParts, shortRegion } from '../event-search.js';

// 행사 목록 (NSU-116): 가까운 날짜 순. 행을 누르면 목록 자리에 장소가 나옵니다.
// title: '서울특별시' 또는 '전체 지역', events: 지금 보이는 행사, total: 그 지역 전체 행사 수
// onMore: 더 보기, onClearRegion: 지역 칩의 ✕ (한 단계 넓은 지역으로), clearLabel: 빈 목록의 되돌리기 버튼 이름
// onOpen(id): 행을 누름
// pinNumbers: { 행사 id: 번호 } 행 앞에 지도 핀과 같은 번호를 붙임 (NSU-117). null이면 번호 없음
//   번호가 없는 행사는 좌표가 없어 지도에 핀이 없는 행사 ('지도 위치 없음')
// hotId: 지도 핀에 마우스를 올린 행사
// onHot(id | ''): 행에 마우스를 올리거나 포커스가 들어오고 나감 (지도 핀이 함께 강조됨)
export default function EventList({
  title,
  events,
  total,
  onMore,
  regionSelected,
  clearLabel = '전체 지역 보기',
  onClearRegion,
  onOpen,
  pinNumbers = null,
  hotId = '',
  onHot = () => {},
}) {
  // 마우스가 올라간 행과 키보드 포커스가 있는 행을 따로 기억 (핀 유무와 상관없이 행사 id)
  // 강조할 핀은 지금 번호(pinNumbers)로 정함: 마우스 행에 핀이 있으면 그 핀, 없으면 포커스 행의 핀
  // 둘 다 핀이 없을 때만 강조를 풂
  const hovered = useRef('');
  const focused = useRef('');
  const withPin = (id) => (id && pinNumbers?.[id] ? id : '');
  const showHot = () => onHot(withPin(hovered.current) || withPin(focused.current));
  // 지도를 다 받기 전에 포커스한 행도, 번호가 생기면 바로 핀을 강조
  const pinKey = pinNumbers ? Object.keys(pinNumbers).join() : '';
  useEffect(() => {
    if (hovered.current || focused.current) showHot();
    // 번호 목록이 바뀔 때만 다시 계산 (showHot은 매 렌더 새로 만들어짐)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pinKey]);
  return (
    <section
      className="panel es-list"
      aria-labelledby="es-list-title"
    >
      <div className="es-list-head">
        <h2
          id="es-list-title"
          tabIndex={-1}
        >
          {title} <em>{total}건</em>
        </h2>
        <span>가까운 날짜 순</span>
      </div>
      {regionSelected && (
        <div className="es-filter">
          <span className="es-filter-chip">
            지역 <strong>{title}</strong>
            <button
              type="button"
              aria-label={`${title} 지역 선택 풀기`}
              onClick={onClearRegion}
            >
              <Icon
                name="close"
                size={14}
              />
            </button>
          </span>
          <small>지도에서 고른 지역 행사만 보여요.</small>
        </div>
      )}

      {events.length ? (
        <ul className={`es-rows${pinNumbers ? ' is-numbered' : ''}`}>
          {events.map((event) => {
            const { day, when } = dateParts(event);
            const pin = pinNumbers?.[event.id];
            return (
              <li key={event.id}>
                <button
                  type="button"
                  id={`es-row-${event.id}`}
                  className={`es-row${hotId === event.id ? ' is-hot' : ''}`}
                  onClick={() => onOpen(event.id)}
                  onPointerEnter={() => {
                    hovered.current = event.id;
                    showHot();
                  }}
                  onPointerLeave={() => {
                    hovered.current = '';
                    showHot();
                  }}
                  onFocus={() => {
                    focused.current = event.id;
                    showHot();
                  }}
                  onBlur={() => {
                    focused.current = '';
                    showHot();
                  }}
                >
                  {pinNumbers && (
                    <span
                      className={`es-row-n${pin ? '' : ' is-none'}`}
                      aria-hidden="true"
                    >
                      {pin}
                    </span>
                  )}
                  <span className="es-row-date">
                    <strong>{day}</strong>
                    <small>{when}</small>
                  </span>
                  <span className="es-row-main">
                    <strong>{event.name}</strong>
                    <small>
                      {shortRegion(event)}, {event.venue}
                      {pinNumbers && !pin && <span className="es-no-pin">지도 위치 없음</span>}
                    </small>
                    <span className="tags">
                      <Tag>{event.eventType}</Tag>
                      {event.genres.map((g) => (
                        <Tag
                          key={g}
                          tone="subtle"
                        >
                          {g}
                        </Tag>
                      ))}
                    </span>
                  </span>
                  <span className="es-row-budget">
                    <strong>
                      <span className="es-sr-only">예산 </span>
                      {budgetText(event)}
                    </strong>
                    <small>공연 {event.performanceMinutes}분</small>
                  </span>
                  <span
                    className="es-row-go"
                    aria-hidden="true"
                  >
                    <Icon
                      name="chevron-right"
                      size={18}
                    />
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      ) : null}
      {events.length > 0 && total > events.length && (
        <button
          type="button"
          className="es-more"
          onClick={onMore}
        >
          행사 더 보기 ({total - events.length}건 남음)
          <Icon
            name="chevron-down"
            size={16}
          />
        </button>
      )}
      {total === 0 && (
        <div className="empty-state es-empty">
          <h3>이 지역에는 아직 공개 행사가 없어요.</h3>
          <p>지도에서 다른 지역을 고르거나 더 넓은 지역의 행사를 둘러보세요.</p>
          <button
            type="button"
            className="secondary"
            onClick={onClearRegion}
          >
            {clearLabel}
          </button>
        </div>
      )}
    </section>
  );
}

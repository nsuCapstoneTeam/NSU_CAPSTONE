import Icon from '../../../components/common/icon.jsx';
import Tag from '../../../components/common/tag.jsx';
import { budgetText } from '../../my-page/organizer-events.js';
import { dateParts, shortRegion } from '../event-search.js';

// 행사 목록 (NSU-116): 가까운 날짜 순. 행을 누르면 목록 자리에 장소가 나옵니다.
// title: '서울특별시' 또는 '전체 지역', events: 지금 보이는 행사, total: 그 지역 전체 행사 수
// onMore: 더 보기, onClearRegion: 지역 칩의 ✕ (전체로 돌아가기), onOpen(id): 행을 누름
export default function EventList({ title, events, total, onMore, regionSelected, onClearRegion, onOpen }) {
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
          <small>고른 지역 행사만 보여요.</small>
        </div>
      )}

      {events.length ? (
        <ul className="es-rows">
          {events.map((event) => {
            const { day, when } = dateParts(event);
            return (
              <li key={event.id}>
                <button
                  type="button"
                  id={`es-row-${event.id}`}
                  className="es-row"
                  onClick={() => onOpen(event.id)}
                >
                  <span className="es-row-date">
                    <strong>{day}</strong>
                    <small>{when}</small>
                  </span>
                  <span className="es-row-main">
                    <strong>{event.name}</strong>
                    <small>
                      {shortRegion(event)}, {event.venue}
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
          <p>다른 지역을 고르거나 전체 지역 행사를 둘러보세요.</p>
          <button
            type="button"
            className="secondary"
            onClick={onClearRegion}
          >
            전체 지역 보기
          </button>
        </div>
      )}
    </section>
  );
}

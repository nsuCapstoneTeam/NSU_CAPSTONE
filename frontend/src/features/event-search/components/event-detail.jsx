import Icon from '../../../components/common/icon.jsx';
import Tag from '../../../components/common/tag.jsx';
import { dayTitle } from '../../my-page/availability.js';
import { formatBytes } from '../../my-page/event-place.js';
import { budgetText } from '../../my-page/organizer-events.js';
import { shortRegion } from '../event-search.js';
import EventPlaceBlock from './event-place-block.jsx';

// 행사 상세 (NSU-116): 행사 등록(NSU-115)과 같은 항목을 같은 순서로 보여 줍니다.
// 일정·조건 → 장소 → 행사 설명 → 특이사항 → 무대 도면, 오른쪽에 예산과 지원하기
// [체험판] 지원하기(APP-057)는 API와 지원서 내용이 정해지지 않아 안내만 합니다 (팀 확정 필요).
export default function EventDetail({ event, onBack, onApply }) {
  const facts = [
    ['비워 둘 시간', `${dayTitle(event.date)} ${event.startTime}~${event.endTime}`],
    ['공연 시간', `그중 ${event.performanceMinutes}분`],
    ['원하는 공연', event.artistType],
    ['장르', event.genres.join(', ')],
    ['예상 관객', `${event.audience.toLocaleString()}명`],
    ['예산', budgetText(event)],
  ];
  const plan = event.stagePlan;
  const planKind = plan ? (plan.name.split('.').pop() || '').toUpperCase() : '';

  return (
    <article
      className="es-detail"
      aria-labelledby="es-detail-title"
    >
      <button
        type="button"
        className="text-button es-back"
        onClick={onBack}
      >
        <Icon
          name="chevron-left"
          size={16}
        />
        행사 위치로 돌아가기
      </button>
      <header className="es-detail-head">
        <div className="tags">
          <Tag>{event.eventType}</Tag>
        </div>
        <h1
          id="es-detail-title"
          tabIndex={-1}
        >
          {event.name}
        </h1>
        <p>
          {shortRegion(event)}, {event.venue}
        </p>
      </header>

      <div className="es-detail-layout">
        <div className="es-detail-main">
          <section className="panel es-card">
            <h2>일정·조건</h2>
            <dl className="es-facts">
              {facts.map(([label, value]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
            <p className="es-card-note">
              비워 둘 시간은 아티스트가 그날 비워 둬야 하는 시간대이고, 공연 시간은 그 안에서 무대에 서는
              길이예요.
            </p>
          </section>

          <section className="panel es-card">
            <h2>장소</h2>
            <EventPlaceBlock event={event} />
          </section>

          <section className="panel es-card">
            <h2>행사 설명</h2>
            <p className="es-text">{event.description}</p>
          </section>

          <section className="panel es-card">
            <h2>
              특이사항 <small>행사 관계자가 적은 내용이에요</small>
            </h2>
            {event.notes ? (
              <p className="es-text">{event.notes}</p>
            ) : (
              <p className="es-none">따로 적은 특이사항이 없어요.</p>
            )}
          </section>

          <section className="panel es-card">
            <h2>무대 도면</h2>
            {plan ? (
              <div className="es-plan">
                <span
                  className="es-plan-icon"
                  aria-hidden="true"
                >
                  <Icon
                    name="file"
                    size={24}
                  />
                </span>
                <span>
                  <strong>{plan.name}</strong>
                  <small>
                    {planKind} 파일, {formatBytes(plan.size)}
                  </small>
                </span>
                <small className="es-plan-note">체험판이라 도면 파일은 열어 볼 수 없어요.</small>
              </div>
            ) : (
              <p className="es-none">행사 관계자가 올린 도면이 없어요. 무대 정보는 특이사항을 확인해 주세요.</p>
            )}
          </section>
        </div>

        <aside
          className="panel es-apply"
          aria-label="예산과 지원하기"
        >
          <span className="es-apply-label">예산</span>
          <strong className="es-apply-budget">{budgetText(event)}</strong>
          <p>출연료는 지원할 때 적고, 행사 관계자와 협의해서 정해요.</p>
          <button
            type="button"
            className="primary full"
            onClick={onApply}
          >
            이 행사에 지원하기
          </button>
          <small>체험판에서는 지원서를 보내지 않아요.</small>
        </aside>
      </div>
    </article>
  );
}

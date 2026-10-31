import Icon from '../../../components/common/icon.jsx';
import { dayTitle } from '../../my-page/availability.js';
import EventPlaceBlock from './event-place-block.jsx';

// 장소 보기 (NSU-116): 목록에서 행을 누르면 목록 자리에 행사 장소만 간단히 보여 줍니다.
// backLabel: '서울특별시 행사 18건'처럼 돌아갈 목록 이름
export default function EventPlacePanel({ event, backLabel, onBack, onOpenDetail }) {
  return (
    <section
      className="panel es-place"
      aria-labelledby="es-place-title"
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
        {backLabel}
      </button>
      <div className="es-place-head">
        <h2
          id="es-place-title"
          tabIndex={-1}
        >
          {event.name}
        </h2>
        <p>
          {dayTitle(event.date)} {event.startTime}~{event.endTime}, 공연 {event.performanceMinutes}분
        </p>
      </div>
      <h3>장소</h3>
      <EventPlaceBlock event={event} />
      <button
        type="button"
        className="primary full"
        onClick={onOpenDetail}
      >
        상세사항 보기
      </button>
    </section>
  );
}

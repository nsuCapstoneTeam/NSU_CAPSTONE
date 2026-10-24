import { useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import EventFields from './event-fields.jsx';
import { formatDuration } from '../reference-song.js';
import { referenceTrack } from '../song-results.js';
import { validateEvent } from '../matching.js';

// 참고 곡으로 찾기 결과 화면 왼쪽 카드 (NSU-111)
// 올린 참고 곡(듣기·다른 곡으로 찾기) + 행사 조건(바뀐 항목 표시·조건 바꾸기)

// 행사 조건 요약 줄: [이름, 값을 만드는 함수, 비교할 칸들]
const ROWS = [
  ['날짜', (e) => `${e.date} ${e.start}`, ['date', 'start']],
  ['지역', (e) => e.region, ['region']],
  ['예산', (e) => `최대 ${e.budget}만 원`, ['budget']],
  ['공연 시간', (e) => `${e.minutes}분`, ['minutes']],
];
// 참고 곡 카드의 장식용 파형 막대 높이
const WAVE = [6, 10, 16, 12, 20, 26, 18, 14, 22, 28, 20, 12, 16, 24, 18, 10, 14, 20];

// event: 지금 행사 조건, snapshot: 추천을 받을 때의 행사 조건, onSaveEvent(새 조건), onNewSong: 다른 곡으로 찾기
export default function SongSummary({ modeSwitch, reference, player, event, snapshot, onSaveEvent, onNewSong }) {
  // draft: 고치는 중인 조건 (null이면 요약 보기), draftError: 저장 전 입력 확인 결과
  const [draft, setDraft] = useState(null);
  const [draftError, setDraftError] = useState('');
  const track = referenceTrack(reference);
  const playing = player.key === track.key && player.playing;
  const changed = (keys) => keys.some((k) => String(event[k]) !== String(snapshot[k]));

  // 처음 찾을 때와 같은 입력 확인을 거친 뒤에만 저장
  function save(e) {
    e.preventDefault();
    const message = validateEvent(draft);
    setDraftError(message);
    if (message) return;
    onSaveEvent(draft);
    setDraft(null);
  }
  function openEdit() {
    setDraftError('');
    setDraft({ ...event });
  }

  return (
    <div className="panel match-form song-summary">
      {modeSwitch}

      <div className="song-ref">
        <p className="song-ref-head">
          <span>내가 올린 참고 곡</span>
          <span>{formatDuration(reference.duration)}</span>
        </p>
        <strong>{reference.name}</strong>
        <div className="song-ref-play">
          <button
            type="button"
            onClick={() => player.toggle(track)}
            aria-label={playing ? '참고 곡 일시정지' : '참고 곡 듣기'}
          >
            <Icon
              name={playing ? 'pause' : 'play'}
              size={14}
            />
          </button>
          <span
            className="song-ref-wave"
            aria-hidden="true"
          >
            {WAVE.map((h, i) => (
              <i
                key={i}
                style={{ height: `${h}px` }}
              />
            ))}
          </span>
        </div>
      </div>
      <button
        type="button"
        className="secondary full"
        onClick={onNewSong}
      >
        다른 곡으로 찾기
      </button>

      <div className="form-divider" />
      {draft ? (
        <form
          className="song-edit"
          onSubmit={save}
        >
          <h2 className="song-form-title">행사 조건 바꾸기</h2>
          <EventFields
            form={draft}
            change={(name, value) => setDraft((prev) => ({ ...prev, [name]: value }))}
          />
          {draftError && (
            <p
              role="alert"
              className="error"
            >
              {draftError}
            </p>
          )}
          <div className="song-edit-actions">
            <button
              type="button"
              className="secondary"
              onClick={() => setDraft(null)}
            >
              취소
            </button>
            <button
              type="submit"
              className="primary"
            >
              조건 저장
            </button>
          </div>
        </form>
      ) : (
        <>
          <p className="song-event-label">행사 조건</p>
          <h2 className="song-event-name">{event.title}</h2>
          <dl className="song-event">
            {ROWS.map(([label, value, keys]) => (
              <div key={label}>
                <dt>
                  {label}
                  {changed(keys) && <span className="song-changed">바뀜</span>}
                </dt>
                <dd className={changed(keys) ? 'is-changed' : ''}>{value(event)}</dd>
              </div>
            ))}
          </dl>
          <button
            type="button"
            className="secondary full"
            onClick={openEdit}
          >
            조건 바꾸기
          </button>
          <p className="field-note">곡은 참고 곡으로 찾고, 일정·지역·예산은 행사 조건으로 걸러요.</p>
        </>
      )}
    </div>
  );
}

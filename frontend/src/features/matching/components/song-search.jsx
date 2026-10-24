import { useEffect, useRef, useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import SongSearchForm from './song-search-form.jsx';
import SongSummary from './song-summary.jsx';
import SongResultList from './song-result-list.jsx';
import useSongPlayer from '../use-song-player.js';
import { buildSongResults } from '../song-results.js';
import { validateEvent } from '../matching.js';
import '../song-search.css';

// 참고 곡으로 찾기 (NSU-111): 왼쪽 입력/요약 카드 + 오른쪽 결과
// 참고 곡 찾기는 아직 SSOT·API 계약에 없어 체험용으로만 동작합니다. 파일은 서버로 보내지 않습니다.
// form·setForm: 행사 설명으로 찾기와 함께 쓰는 행사 조건, hidden: 다른 방법을 고른 동안 숨김

// 같은 행사는 1분에 한 번만 다시 추천받을 수 있음 (계약의 429 규칙과 같은 간격)
const RETRY_GAP_MS = 60 * 1000;
// 행사 조건 중 바뀌면 '조건 바뀜' 안내를 띄우는 칸
const EVENT_KEYS = ['title', 'date', 'start', 'region', 'budget', 'minutes'];

export default function SongSearch({ hidden, modeSwitch, form, setForm, onResultsChange }) {
  const [reference, setReferenceState] = useState(null);
  // results: buildSongResults 결과 + event(추천을 받을 때의 행사 조건)
  const [results, setResults] = useState(null);
  const [error, setError] = useState('');
  const [retryNote, setRetryNote] = useState('');
  const resultsRef = useRef(null);
  const player = useSongPlayer();
  const { stop } = player;

  // 다른 찾는 방법으로 바꾸면 소리를 끔
  useEffect(() => {
    if (hidden) stop();
  }, [hidden, stop]);
  // 결과가 생기거나 사라지면 매칭 화면에 알림 (배경 사진 전환)
  useEffect(() => {
    onResultsChange?.(Boolean(results));
  }, [results, onResultsChange]);

  const change = (name, value) => setForm((prev) => ({ ...prev, [name]: value }));

  // 참고 곡 파일 주소 정리: 다른 파일로 바뀌거나 매칭 화면을 떠나면 이전 주소를 해제
  const referenceUrl = reference?.url;
  useEffect(
    () => () => {
      if (referenceUrl) URL.revokeObjectURL(referenceUrl);
    },
    [referenceUrl],
  );

  // 참고 곡을 바꾸면 재생을 멈춤
  function setReference(next) {
    stop();
    setReferenceState(next);
  }

  // 입력 확인 → 체험용 결과 만들기 → 결과로 포커스 이동
  function submit(e) {
    e.preventDefault();
    const message = validateEvent(form);
    setError(message);
    if (message || reference?.status !== 'ready') return;
    setResults({ ...buildSongResults(new Date()), event: { ...form } });
    setRetryNote('');
    setTimeout(() => resultsRef.current?.focus(), 0);
  }

  // 결과 화면에서 행사 조건을 바꿈 → 추천을 받을 때와 다르면 '조건 바뀜' 안내
  function saveEvent(next) {
    setForm(next);
    setResults((prev) => ({
      ...prev,
      isEventChanged: EVENT_KEYS.some((k) => String(next[k]) !== String(prev.event[k])),
    }));
  }

  // 바뀐 조건으로 다시 추천받기 (같은 행사는 1분 간격)
  function retry() {
    const wait = RETRY_GAP_MS - (Date.now() - new Date(results.createdAt).getTime());
    if (wait > 0) {
      setRetryNote(`같은 행사는 1분에 한 번만 다시 받을 수 있어요. ${Math.ceil(wait / 1000)}초 뒤에 다시 눌러 주세요.`);
      return;
    }
    stop();
    setResults({ ...buildSongResults(new Date()), event: { ...form } });
    setRetryNote('');
  }

  // 다른 곡으로 찾기: 결과를 닫고 올리는 화면으로 (행사 조건은 그대로)
  function newSong() {
    stop();
    setResults(null);
  }

  return (
    <div
      className="matching-layout song-search"
      hidden={hidden}
    >
      <aside>
        {results ? (
          <SongSummary
            modeSwitch={modeSwitch}
            reference={reference}
            player={player}
            event={form}
            snapshot={results.event}
            onSaveEvent={saveEvent}
            onNewSong={newSong}
          />
        ) : (
          <SongSearchForm
            modeSwitch={modeSwitch}
            form={form}
            change={change}
            reference={reference}
            setReference={setReference}
            error={error}
            onSubmit={submit}
          />
        )}
      </aside>

      <section
        className="match-results"
        ref={resultsRef}
        tabIndex={-1}
        aria-label="참고 곡으로 찾은 추천 결과"
      >
        <div className="model-note">
          <Icon
            name="music"
            size={19}
          />
          <p>
            <strong>지금은 참고 곡으로 찾기 체험판이에요.</strong>
            <br />
            올린 파일은 서버로 보내지 않고 이 브라우저에서만 재생해요. 결과는 예시 값이고, 추천 곡은 실제 음원 대신 그
            곡의 BPM과 박자를 소리로 들려줘요.
          </p>
        </div>
        {results ? (
          <SongResultList
            key={results.recommendationId}
            results={results}
            basis={{ kind: 'song', name: reference.name }}
            reference={reference}
            player={player}
            onRetry={retry}
            retryNote={retryNote}
          />
        ) : (
          <div className="empty-state match-empty">
            <div className="empty-icon">
              <Icon
                name="upload"
                size={32}
              />
            </div>
            <h2>참고 곡을 올려 주세요</h2>
            <p>
              원하는 분위기의 곡을 올리면,
              <br />그 곡과 비슷한 곡을 가진 아티스트를 찾아요.
            </p>
          </div>
        )}
      </section>
    </div>
  );
}

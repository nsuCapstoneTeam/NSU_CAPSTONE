import { useId, useRef, useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import {
  ACCEPT,
  UPLOAD_NOTICE,
  checkDuration,
  checkFileBasics,
  formatDuration,
  formatSize,
} from '../reference-song.js';

// 참고 곡 올리는 곳 (NSU-111): 끌어다 놓기·파일 고르기 → 형식·크기·길이 확인 → 결과 표시
// 파일은 서버로 보내지 않고 브라우저 안에서만 재생합니다.
// value: { name, size, url, duration, status: 'checking' | 'ready' | 'error', error }, onChange(value | null)

// 길이를 읽을 때 기다리는 최대 시간. 브라우저가 정보를 주지 않아도 '확인 중'에 멈춰 있지 않게 함
const READ_TIMEOUT_MS = 10 * 1000;

// 파일 길이(초)를 읽음. 읽지 못하거나 시간이 지나면 NaN (→ '재생할 수 없는 파일' 안내)
function readDuration(url) {
  return new Promise((resolve) => {
    const audio = new Audio();
    let timer = null;
    // 결과를 한 번만 넘기고, 길이를 읽으려고 만든 audio 요소를 정리함
    const finish = (seconds) => {
      clearTimeout(timer);
      audio.onloadedmetadata = null;
      audio.onerror = null;
      audio.removeAttribute('src');
      audio.load();
      resolve(seconds);
    };
    timer = setTimeout(() => finish(NaN), READ_TIMEOUT_MS);
    audio.preload = 'metadata';
    audio.onloadedmetadata = () => finish(audio.duration);
    audio.onerror = () => finish(NaN);
    audio.src = url;
  });
}

export default function ReferenceUpload({ value, onChange }) {
  const inputRef = useRef(null);
  const [dragging, setDragging] = useState(false);
  const noticeId = useId();
  // 마지막으로 고른 파일 (늦게 끝난 길이 확인이 새 파일 결과를 덮어쓰지 않게)
  // 파일 주소는 결과 화면에서도 재생하므로 여기서 정리하지 않습니다. 정리는 SongSearch가 함 (바꿀 때·화면을 떠날 때)
  const latestRef = useRef(null);

  async function pick(file) {
    if (!file) return;
    if (value?.url) URL.revokeObjectURL(value.url);
    const basic = checkFileBasics(file);
    if (basic) {
      latestRef.current = null;
      onChange({ name: file.name, size: file.size, status: 'error', error: basic });
      return;
    }
    const url = URL.createObjectURL(file);
    latestRef.current = url;
    onChange({ name: file.name, size: file.size, url, status: 'checking' });
    const duration = await readDuration(url);
    if (latestRef.current !== url) return;
    const problem = checkDuration(duration);
    if (problem) URL.revokeObjectURL(url);
    onChange(
      problem
        ? { name: file.name, size: file.size, status: 'error', error: problem }
        : { name: file.name, size: file.size, url, duration, status: 'ready' },
    );
  }

  function drop(e) {
    e.preventDefault();
    setDragging(false);
    pick(e.dataTransfer.files?.[0]);
  }

  const chooseButton = (label, className = 'primary') => (
    <button
      type="button"
      className={className}
      onClick={() => inputRef.current?.click()}
    >
      {label}
    </button>
  );

  return (
    <div className="ref-upload">
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT}
        className="sr-only"
        tabIndex={-1}
        aria-hidden="true"
        onChange={(e) => {
          pick(e.target.files?.[0]);
          e.target.value = '';
        }}
      />

      {/* 파일을 고르기 전 / 오류 뒤: 끌어다 놓는 곳 */}
      {(!value || value.status === 'error') && (
        <div
          className={`ref-drop${dragging ? ' is-dragging' : ''}`}
          onDragOver={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={drop}
          aria-describedby={noticeId}
        >
          <span
            className="ref-drop-icon"
            aria-hidden="true"
          >
            <Icon
              name="upload"
              size={22}
            />
          </span>
          <p>여기에 곡 파일을 끌어다 놓거나</p>
          {chooseButton('파일 고르기')}
        </div>
      )}

      {/* 확인 결과 */}
      {value && (
        <div
          className={`ref-file is-${value.status}`}
          role={value.status === 'error' ? 'alert' : 'status'}
        >
          <span
            className="ref-file-icon"
            aria-hidden="true"
          >
            <Icon
              name={value.status === 'error' ? 'info' : 'music'}
              size={18}
            />
          </span>
          {value.status === 'error' ? (
            <p>
              <strong>{value.error.title}</strong>
              <span>{value.error.fix}</span>
            </p>
          ) : (
            <p>
              <strong>{value.name}</strong>
              <span>
                {value.status === 'checking'
                  ? '파일을 확인하는 중…'
                  : `${formatDuration(value.duration)} · ${formatSize(value.size)} · 올릴 수 있어요`}
              </span>
            </p>
          )}
          {value.status === 'ready' && chooseButton('다른 파일로 바꾸기', 'text-button')}
        </div>
      )}

      {/* 경고문: 임시 규칙 (서버 협의 013과 같은 값, 팀 확정 필요) */}
      <div
        className="ref-notice"
        id={noticeId}
      >
        <strong>
          <Icon
            name="info"
            size={16}
          />
          올리기 전에 확인해 주세요
        </strong>
        <ul>
          {UPLOAD_NOTICE.map((text) => (
            <li key={text}>{text}</li>
          ))}
        </ul>
      </div>
    </div>
  );
}

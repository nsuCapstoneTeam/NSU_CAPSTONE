import { useEffect, useRef, useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import { STAGE_PLAN_ACCEPT, checkStagePlan, formatBytes, stagePlanType } from '../event-place.js';
import StagePlanExample from './stage-plan-example.jsx';

// 무대 도면 (NSU-115, 선택): 도면 한 장을 끌어다 놓거나 골라 올립니다.
// [체험판] 파일은 서버로 보내지 않고 이 화면에서만 미리 봅니다. 행사에는 파일 이름·크기만 저장돼요.
// 파일 형식·크기(JPG·PNG·PDF, 10MB 이하)는 임시 값입니다 (팀 확정 필요).
// value: { name, size, type } | null, onChange(value)

export default function StagePlanField({ value, onChange }) {
  const inputRef = useRef(null);
  const viewerRef = useRef(null);
  const [error, setError] = useState('');
  const [dragging, setDragging] = useState(false);
  const [exampleOpen, setExampleOpen] = useState(false);
  // 이번에 고른 파일의 미리 보기 주소 (저장된 행사를 다시 열면 없음)
  const [previewUrl, setPreviewUrl] = useState('');

  // 다른 파일로 바꾸거나 폼을 닫으면 이전 미리 보기 주소를 해제
  useEffect(
    () => () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    },
    [previewUrl],
  );

  function take(file) {
    if (!file) return;
    const message = checkStagePlan(file);
    setError(message);
    if (message) return;
    // 미리 보기 주소는 확장자로 정한 종류로만 만듦 (PDF는 PDF 보기로, 이미지는 그림으로만 열림)
    const type = stagePlanType(file.name);
    setPreviewUrl(URL.createObjectURL(new Blob([file], { type })));
    onChange({ name: file.name, size: file.size, type });
  }

  function clear() {
    setPreviewUrl('');
    setError('');
    onChange(null);
    window.requestAnimationFrame(() => document.getElementById('event-stagePlan')?.focus());
  }

  const isImage = value?.type?.startsWith('image/');
  const kind = value ? (value.name.split('.').pop() || '').toUpperCase() : '';

  return (
    <div
      className="stage-plan"
      role="group"
      aria-labelledby="event-stagePlan-label"
    >
      <span
        id="event-stagePlan-label"
        className="place-field-label"
      >
        무대 도면 <small>선택</small>
      </span>
      <input
        ref={inputRef}
        type="file"
        accept={STAGE_PLAN_ACCEPT}
        hidden
        onChange={(e) => {
          take(e.target.files?.[0]);
          e.target.value = '';
        }}
      />

      {value ? (
        <div className="stage-plan-file">
          <span className="stage-plan-thumb">
            {isImage && previewUrl ? (
              <img
                src={previewUrl}
                alt=""
              />
            ) : (
              <>
                <Icon
                  name="file"
                  size={26}
                />
                <small>{kind}</small>
              </>
            )}
          </span>
          <span className="stage-plan-info">
            <strong>{value.name}</strong>
            <small>
              {kind} · {formatBytes(value.size)}
              {!previewUrl && ' · 체험판이라 파일 이름만 저장돼서 미리 볼 수 없어요'}
            </small>
          </span>
          <span className="stage-plan-actions">
            {previewUrl &&
              (isImage ? (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => viewerRef.current?.showModal()}
                >
                  크게 보기
                </button>
              ) : (
                <a
                  className="secondary"
                  href={previewUrl}
                  target="_blank"
                  rel="noreferrer"
                >
                  크게 보기
                </a>
              ))}
            <button
              type="button"
              className="secondary"
              onClick={() => inputRef.current?.click()}
            >
              바꾸기
            </button>
            <button
              type="button"
              className="secondary stage-plan-remove"
              onClick={clear}
            >
              지우기
            </button>
          </span>
        </div>
      ) : (
        <button
          id="event-stagePlan"
          type="button"
          className={`stage-plan-drop${dragging ? ' is-dragging' : ''}${error ? ' has-error' : ''}`}
          onClick={() => inputRef.current?.click()}
          onDragOver={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragging(false);
            take(e.dataTransfer.files?.[0]);
          }}
          aria-describedby="event-stagePlan-help"
        >
          <Icon
            name="upload"
            size={24}
          />
          <strong>도면 파일을 끌어다 놓거나 눌러서 골라 주세요</strong>
          <small>JPG · PNG · PDF, 10MB 이하</small>
        </button>
      )}

      {error && (
        <p
          className="stage-plan-error"
          role="alert"
        >
          {error}
        </p>
      )}

      <p
        id="event-stagePlan-help"
        className="stage-plan-help"
      >
        <small>무대 가로·세로 길이와 대기실 위치가 보이면 좋아요. 정식 도면이 없으면 손그림이나 무대 사진도 괜찮아요.</small>
        <button
          type="button"
          className="text-button"
          onClick={() => setExampleOpen(true)}
        >
          도면 예시 보기
        </button>
      </p>

      <StagePlanExample
        open={exampleOpen}
        onClose={() => setExampleOpen(false)}
      />

      {/* 이미지 도면 크게 보기 */}
      {isImage && previewUrl && (
        <dialog
          ref={viewerRef}
          className="plan-dialog plan-viewer"
          aria-label={`${value.name} 크게 보기`}
        >
          <img
            src={previewUrl}
            alt={`무대 도면 ${value.name}`}
          />
          <button
            type="button"
            className="primary"
            onClick={() => viewerRef.current?.close()}
          >
            닫기
          </button>
        </dialog>
      )}
    </div>
  );
}

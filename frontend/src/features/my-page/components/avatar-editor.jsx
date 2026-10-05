import { useEffect, useRef, useState } from 'react';
import Avatar from './avatar.jsx';
import { AVATAR_COLORS } from '../avatar-colors.js';
import './avatar-editor.css';

// 프로필 사진 꾸미기 (ART-027 프로필 이미지)
// 사진을 고르면 원형 틀 안에서 확대·위치를 맞추고, 256px로 줄여 이 브라우저에만 저장합니다.
// 사진 대신 이니셜 + 배경색으로 표시할 수도 있습니다. 서버로는 보내지 않습니다.

const VIEW = 200; // 편집 틀 크기(px)
const OUT = 256; // 저장할 사진 크기(px)
const MAX_FILE = 10 * 1024 * 1024; // 10MB
const MIN_ZOOM = 1;
const MAX_ZOOM = 3;
const MOVE_KEYS = { ArrowLeft: [-8, 0], ArrowRight: [8, 0], ArrowUp: [0, -8], ArrowDown: [0, 8] };

export default function AvatarEditor({ profile, onSave, notify }) {
  // 편집 중인 사진 { url, w, h, el } (없으면 편집 모드가 아님)
  const [image, setImage] = useState(null);
  const [zoom, setZoom] = useState(MIN_ZOOM);
  // 사진 가운데가 틀 가운데에서 얼마나 옮겨졌는지(px)
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const dragRef = useRef(null);
  const cropRef = useRef(null);
  const fileRef = useRef(null);

  // 편집을 시작하면 편집 틀로 포커스, 사진이 바뀌거나 편집이 끝나면 임시 주소 해제
  useEffect(() => {
    if (!image) return;
    cropRef.current?.focus();
    return () => URL.revokeObjectURL(image.url);
  }, [image]);

  const avatar = profile.avatar;
  // 틀을 꽉 채우는 배율 × 확대 배율
  const scaleOf = (z) => (image ? (VIEW / Math.min(image.w, image.h)) * z : 1);
  const dispW = image ? image.w * scaleOf(zoom) : 0;
  const dispH = image ? image.h * scaleOf(zoom) : 0;

  // 사진이 틀 밖으로 비지 않도록 이동 범위를 제한
  function clamp(next, z) {
    const limX = (image.w * scaleOf(z) - VIEW) / 2;
    const limY = (image.h * scaleOf(z) - VIEW) / 2;
    return {
      x: Math.max(-limX, Math.min(limX, next.x)),
      y: Math.max(-limY, Math.min(limY, next.y)),
    };
  }

  function changeZoom(z) {
    const next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    setZoom(next);
    setOffset((prev) => clamp(prev, next));
  }

  function chooseFile(e) {
    const file = e.target.files[0];
    e.target.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      notify('이미지 파일을 선택해 주세요.');
      return;
    }
    if (file.size > MAX_FILE) {
      notify('10MB 이하의 사진을 선택해 주세요.');
      return;
    }
    const url = URL.createObjectURL(file);
    const el = new Image();
    el.onload = () => {
      setZoom(MIN_ZOOM);
      setOffset({ x: 0, y: 0 });
      setImage({ url, w: el.naturalWidth, h: el.naturalHeight, el });
    };
    el.onerror = () => {
      URL.revokeObjectURL(url);
      notify('사진을 열 수 없어요. 다른 파일을 선택해 주세요.');
    };
    el.src = url;
  }

  // 끌어서 위치 옮기기 (마우스·터치 공통)
  function pointerDown(e) {
    e.currentTarget.setPointerCapture(e.pointerId);
    dragRef.current = { sx: e.clientX, sy: e.clientY, ox: offset.x, oy: offset.y };
  }
  function pointerMove(e) {
    const d = dragRef.current;
    if (!d) return;
    setOffset(clamp({ x: d.ox + e.clientX - d.sx, y: d.oy + e.clientY - d.sy }, zoom));
  }
  function pointerUp() {
    dragRef.current = null;
  }

  // 키보드: 방향키로 이동, + / - 로 확대·축소
  function cropKeyDown(e) {
    const move = MOVE_KEYS[e.key];
    if (move) {
      e.preventDefault();
      setOffset((prev) => clamp({ x: prev.x + move[0], y: prev.y + move[1] }, zoom));
    } else if (e.key === '+' || e.key === '=') {
      e.preventDefault();
      changeZoom(zoom + 0.1);
    } else if (e.key === '-') {
      e.preventDefault();
      changeZoom(zoom - 0.1);
    }
  }

  function save(next, message) {
    const saved = onSave(next);
    notify(
      saved
        ? message
        : '화면에는 반영됐지만 브라우저 저장에 실패했어요. 저장 공간이 부족할 수 있어요.',
    );
  }

  // 틀 안에 보이는 부분만 256px 사진으로 만들어 저장
  function apply() {
    const canvas = document.createElement('canvas');
    canvas.width = OUT;
    canvas.height = OUT;
    const k = OUT / VIEW;
    canvas
      .getContext('2d')
      .drawImage(
        image.el,
        (VIEW / 2 + offset.x - dispW / 2) * k,
        (VIEW / 2 + offset.y - dispH / 2) * k,
        dispW * k,
        dispH * k,
      );
    // webp를 만들 수 없는 브라우저는 jpeg로 저장
    let src = canvas.toDataURL('image/webp', 0.85);
    if (!src.startsWith('data:image/webp')) src = canvas.toDataURL('image/jpeg', 0.85);
    save({ type: 'photo', src }, '프로필 사진을 저장했어요.');
    setImage(null);
    fileRef.current?.focus();
  }

  function cancel() {
    setImage(null);
    fileRef.current?.focus();
  }

  return (
    <section className="panel avatar-panel">
      <h2>프로필 사진</h2>

      {image ? (
        // 편집 모드: 원형 틀 + 확대 슬라이더
        <div className="avatar-crop-area">
          <div
            ref={cropRef}
            className="avatar-crop"
            style={{ width: VIEW, height: VIEW }}
            tabIndex={0}
            role="group"
            aria-label="사진 위치 조절. 끌거나 방향키로 옮기고, 더하기·빼기 키로 확대·축소해요."
            onPointerDown={pointerDown}
            onPointerMove={pointerMove}
            onPointerUp={pointerUp}
            onPointerCancel={pointerUp}
            onKeyDown={cropKeyDown}
          >
            <img
              src={image.url}
              alt=""
              draggable={false}
              style={{
                width: dispW,
                height: dispH,
                transform: `translate(${offset.x - dispW / 2}px, ${offset.y - dispH / 2}px)`,
              }}
            />
          </div>
          <label className="avatar-zoom">
            확대
            <input
              type="range"
              min={MIN_ZOOM}
              max={MAX_ZOOM}
              step="0.05"
              value={zoom}
              onChange={(e) => changeZoom(Number(e.target.value))}
            />
          </label>
          <p className="avatar-help">사진을 끌어서 얼굴이 가운데 오도록 맞춰 주세요.</p>
          <div className="avatar-actions">
            <button
              type="button"
              className="primary"
              onClick={apply}
            >
              이 사진으로 저장
            </button>
            <button
              type="button"
              className="text-button"
              onClick={cancel}
            >
              취소
            </button>
          </div>
        </div>
      ) : (
        <>
          <div className="avatar-row">
            <Avatar
              profile={profile}
              size={96}
            />
            <div className="avatar-actions">
              <label className="avatar-file secondary">
                사진 선택
                <input
                  ref={fileRef}
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  onChange={chooseFile}
                />
              </label>
              {avatar?.type === 'photo' && (
                <button
                  type="button"
                  className="text-button danger"
                  onClick={() =>
                    save({ type: 'initial', color: 'blue' }, '사진을 지우고 이니셜로 표시해요.')
                  }
                >
                  사진 지우기
                </button>
              )}
            </div>
          </div>

          {/* 사진 대신 이니셜 + 배경색 */}
          <fieldset className="avatar-colors">
            <legend>사진 대신 이니셜로 표시</legend>
            {AVATAR_COLORS.map((c) => (
              <label
                key={c.value}
                className={`avatar-swatch ${c.value}`}
              >
                <input
                  type="radio"
                  name="avatar-color"
                  checked={avatar?.type === 'initial' && avatar.color === c.value}
                  onChange={() =>
                    save({ type: 'initial', color: c.value }, `이니셜을 ${c.label} 배경으로 표시해요.`)
                  }
                />
                <span aria-hidden="true" />
                {c.label}
              </label>
            ))}
          </fieldset>
          <small>
            PNG·JPG·WEBP, 10MB 이하. 사진은 256px로 줄여 이 브라우저에만 저장하고 서버로 보내지
            않아요.
          </small>
        </>
      )}
    </section>
  );
}

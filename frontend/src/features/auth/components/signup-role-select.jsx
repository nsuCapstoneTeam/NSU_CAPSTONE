import { useEffect, useRef, useState } from 'react';
import stageBackground from '../../../assets/login-bg/02-festival-blue-stage.webp';
import './signup-role-select.css';

// 회원가입 역할 선택 화면 (NSU-93)
// 공연 사진 배경 위에 역할 카드 두 장. 카드에 마우스를 올리거나 키보드로 포커스하면 캐릭터 '모모'가 움직입니다.
// - 아티스트: 랩 → 발라드 → 어쿠스틱 → 드럼 순서로 4.3초마다 바뀜
// - 행사 관계자: 체크리스트에 체크하고 종이를 넘김
// - 움직임 줄이기 설정이면 스타일 자동 전환과 애니메이션을 끔
// 그림은 장식이라 화면 낭독기에서는 숨기고, 카드 제목·설명·버튼 글자로 내용을 전달합니다.

const STYLES = ['rap', 'ballad', 'acoustic', 'drum'];
const STYLE_MS = 4300; // 아티스트 스타일이 바뀌는 간격
const READY_MS = 350; // 카드에 들어온 뒤 소품이 나타나기까지 기다리는 시간
const LEAVE_MS = 300; // 스타일이 바뀔 때 이전 소품이 사라지는 시간 (CSS momo-prop-out과 같게)

function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
}

// 아티스트 스타일별 소품 (모자·선글라스·마이크·기타·드럼)
function ArtistProps({ style }) {
  if (style === 'rap') {
    return (
      <>
        {/* 뒤로 쓴 캡 */}
        <g className="momo-cap">
          <path d="M72 59Q82 19 125 27q30 5 39 27z" fill="var(--role-color)" />
          <path d="M87 52q-35-9-49 3q5 10 27 9l32-6z" fill="var(--role-color)" />
          <rect x="119" y="45" width="31" height="11" rx="4" fill="#405673" />
          <path d="M125 51h19" stroke="#c9dfff" strokeWidth="2" />
        </g>

        {/* 선글라스 */}
        <g className="momo-glasses">
          <path
            d="M86 71h28v12q-2 14-14 11q-12-1-14-14z
               M121 71h28v12q-2 14-14 11q-12-1-14-14z"
            fill="#18222f"
            stroke="#4c6680"
            strokeWidth="3"
          />
          <path d="M114 76h7" stroke="#4c6680" strokeWidth="3" />
          <path d="M94 75l6 8M129 75l6 8" stroke="#91baff" strokeWidth="2" />
        </g>

        {/* 마이크 든 손 */}
        <g className="rap-mic">
          <path d="M167 126q21 2 18-17" stroke="#91cbbd" strokeWidth="13" fill="none" strokeLinecap="round" />
          <g transform="rotate(22 185 98)">
            <rect x="177" y="75" width="19" height="29" rx="9" fill="#dbe8f8" />
            <path d="M181 104v29h11v-29" fill="#67798e" />
            <path d="M180 82h13M180 88h13M180 94h13" stroke="#5f748d" strokeWidth="2" />
          </g>
          <ellipse cx="179" cy="118" rx="10" ry="8" fill="#91cbbd" />
        </g>
      </>
    );
  }

  if (style === 'ballad') {
    // 두 손으로 마이크를 감싸 쥠
    return (
      <g>
        <rect x="110" y="110" width="17" height="26" rx="8" fill="#dbe8f8" />
        <path d="M114 134v22h9v-22" fill="#67798e" />
        <path d="M113 118h11M113 124h11" stroke="#64809c" strokeWidth="2" />
        <path
          d="M94 131q12 15 24 9M143 131q-12 15-24 9"
          stroke="#91cbbd"
          strokeWidth="12"
          fill="none"
          strokeLinecap="round"
        />
        <ellipse cx="117" cy="142" rx="9" ry="6" fill="#abdcd0" />
      </g>
    );
  }

  if (style === 'acoustic') {
    return (
      <g>
        {/* 통기타: 넥·헤드·몸통 */}
        <g transform="translate(119 142) rotate(51) scale(.82)">
          <path d="M-6-87h12v61h-12z" fill="#624334" stroke="#402b22" />
          <path d="M-9-111h18l-2 26H-7z" fill="#95653e" stroke="#65402a" strokeWidth="1.8" />
          <g stroke="#bcc7cc" strokeWidth="3" strokeLinecap="round">
            <path d="M-10-106h-4M10-106h4M-9-98h-4M9-98h4M-8-90h-4M8-90h4" />
          </g>
          <path
            d="M-6-31C-18-36-30-28-30-15
               c0 10 11 15 11 23
               c0 8-16 12-16 29
               c0 17 16 26 35 26
               s35-9 35-26
               c0-17-16-21-16-29
               c0-8 11-13 11-23
               c0-13-12-21-24-16z"
            fill="#e9bc7a"
            stroke="#885a35"
            strokeWidth="2.4"
          />
          <circle cy="-9" r="11" fill="#30251e" stroke="#ae7c49" strokeWidth="3" />
          <circle cy="-9" r="15" fill="none" stroke="#8e623c" />
          <path d="M12-4q13 7 7 25l-12-3q2-9-2-13z" fill="#6b4430" />
          <rect x="-15" y="33" width="30" height="8" rx="3" fill="#684632" />
          <path d="M-11 34h22" stroke="#f9e8cb" strokeWidth="2" />
          <g stroke="#d7c0a0">
            <path d="M-6-74H6M-6-64H6M-6-54H6M-6-45H6M-6-37H6" />
          </g>
          <g stroke="#f6e7cd" strokeWidth=".65">
            <path d="M-3-104v139M-1.8-104v139M-.6-104v139M.6-104v139M1.8-104v139M3-104v139" />
          </g>
        </g>

        {/* 코드 잡는 손 + 줄 튕기는 손 */}
        <path d="M163 108l8-4" stroke="#91cbbd" strokeWidth="10" strokeLinecap="round" />
        <g className="strumming-hand">
          <path d="M86 119q11 9 32 22" stroke="#91cbbd" strokeWidth="10" fill="none" strokeLinecap="round" />
          <ellipse cx="121" cy="142" rx="8" ry="6" fill="#abdcd0" />
        </g>
      </g>
    );
  }

  if (style === 'drum') {
    return (
      <g>
        {/* 심벌 두 개 */}
        <path d="M61 143v37M177 134v46" stroke="#8fa2b9" strokeWidth="2" />
        <ellipse cx="60" cy="134" rx="28" ry="5" fill="#dfbd6d" />
        <ellipse cx="181" cy="124" rx="27" ry="5" fill="#dfbd6d" />

        {/* 탐탐 두 개 + 베이스 드럼 */}
        <g fill="#6e91ce" stroke="#c9d8ed" strokeWidth="2">
          <rect x="65" y="143" width="30" height="17" rx="3" />
          <rect x="144" y="138" width="30" height="19" rx="3" />
        </g>
        <circle cx="119" cy="159" r="25" fill="#5076b3" stroke="#c9d8ed" strokeWidth="3" />
        <circle cx="119" cy="159" r="19" fill="#223d63" />

        {/* 스틱 든 두 팔 (번갈아 침) */}
        <g className="drum-arm left">
          <path d="M86 117l-10 9" stroke="#91cbbd" strokeWidth="10" strokeLinecap="round" />
          <path d="M76 125l-15-22" stroke="#e4c897" strokeWidth="3" />
        </g>
        <g className="drum-arm right">
          <path d="M151 113l11 8" stroke="#91cbbd" strokeWidth="10" strokeLinecap="round" />
          <path d="M163 122l20-23" stroke="#e4c897" strokeWidth="3" />
        </g>
      </g>
    );
  }

  return null;
}

// 행사 관계자 소품: 체크리스트 수첩과 펜 든 손
function OrganizerProps() {
  return (
    // organizer-props: 처음 나타날 때 수첩과 손이 아래에서 부드럽게 올라옴
    <g className="organizer-props">
      <g className="momo-notebook">
        <path d="M123 125l51-22 39 40-54 25z" fill="#cd8e78" />
        <path d="M124 121l50-23 39 40-54 25z" fill="#ffeadf" stroke="#d8ab97" strokeWidth="1.5" />
        <path d="M128 123l30 34M133 119l30 34" stroke="#d99c82" strokeWidth="2" />
        <path d="M140 125l27-13M149 135l27-13M162 141l29-14" stroke="#c69c87" strokeWidth="2" />

        {/* 오른쪽에 그려지는 체크 두 개 */}
        <g fill="none" stroke="#d96748" strokeWidth="2.5" strokeLinecap="round">
          <path className="note-check first" d="M177 118l3-8 8 3" />
          <path className="note-check second" d="M186 128l3-8 8 3" />
        </g>
      </g>

      <path d="M154 148q10 6 17 5" stroke="#91cbbd" strokeWidth="10" strokeLinecap="round" fill="none" />
      <g className="writing-hand">
        <path d="M155 112q8 0 8 9" stroke="#91cbbd" strokeWidth="9" strokeLinecap="round" fill="none" />
        <path d="M146 126l18-23" stroke="#ee936e" strokeWidth="4" strokeLinecap="round" />
        <path d="M146 126l-3 6 6-3z" fill="#49382e" />
        <path d="M158 116q-5-4-7 0q-3 4 2 7q5 3 7-1" fill="#91cbbd" stroke="#70ad9d" />
      </g>
    </g>
  );
}

// 캐릭터 모모: 평소에는 가만히, active면 역할에 맞는 소품과 움직임
// leavingStyle: 방금 끝난 스타일. 새 소품이 나타나는 동안 이전 소품이 서서히 사라짐 (교차 전환)
function Momo({ role, active, style, leavingStyle }) {
  const performing = active && role === 'artist' && style;
  const ballad = performing && style === 'ballad';

  return (
    <svg className="momo-art" viewBox="0 0 240 190" aria-hidden="true" focusable="false">
      <ellipse cx="119" cy="172" rx="51" ry="6" fill="var(--role-color)" opacity=".2" />

      {/* momo-pose: 스타일이 바뀔 때마다(key) 살짝 움찔하는 동작으로 자세 변화를 자연스럽게 이어 줌 */}
      <g className="momo-pose" key={style || 'idle'}>
      <g className="momo-body">
        <rect x="62" y="43" width="112" height="112" rx="37" fill="#91cbbd" />
        <path
          d="M81 53q15-6 30-5"
          stroke="#c9eee3"
          strokeWidth="3"
          strokeLinecap="round"
          fill="none"
          opacity=".6"
        />

        <g className="momo-face">
          {ballad ? (
            // 발라드: 눈을 감고 입을 벌려 노래
            <>
              <path
                d="M94 82q7 7 14 0M128 82q7 7 14 0"
                stroke="#244c43"
                strokeWidth="3"
                strokeLinecap="round"
                fill="none"
              />
              <ellipse cx="118" cy="102" rx="5" ry="7" fill="#244c43" />
            </>
          ) : (
            <>
              <circle cx="101" cy="83" r="6" fill="#244c43" />
              <circle cx="135" cy="83" r="6" fill="#244c43" />
              <path d="M113 102h10" stroke="#244c43" strokeWidth="3" strokeLinecap="round" />
            </>
          )}
        </g>

        {/* 이전 소품은 사라지고, 새 소품은 나타남 (key가 바뀌면 등장 애니메이션을 다시 재생) */}
        {performing && leavingStyle && (
          <g className="artist-props is-leaving" key={`leave-${leavingStyle}`}>
            <ArtistProps style={leavingStyle} />
          </g>
        )}
        {performing && (
          <g className="artist-props" key={style}>
            <ArtistProps style={style} />
          </g>
        )}
        {active && role === 'organizer' && <OrganizerProps />}
      </g>
      </g>

      {performing && (
        <g className="floating-notes" fill="var(--role-color)">
          <text x="34" y="83" fontSize="24">♪</text>
          <text x="196" y="51" fontSize="21">♫</text>
        </g>
      )}
    </svg>
  );
}

// 역할 카드: 마우스가 올라가 있거나 카드 안에 포커스가 있는 동안 active
function RoleCard({ role, onSelect }) {
  const artist = role === 'artist';
  const [active, setActive] = useState(false);
  const [styleIndex, setStyleIndex] = useState(0);
  // 카드에 들어온 직후 잠깐 기다렸다가 소품을 보여 주기 위한 값 (스치듯 지나갈 때 깜빡임 방지)
  const [ready, setReady] = useState(false);
  // 방금 끝난 스타일 (사라지는 중인 소품)
  const [leaving, setLeaving] = useState(null);

  const pointerInside = useRef(false);
  // 다음에 카드에 들어왔을 때 보여 줄 스타일 (매번 다른 스타일로 시작)
  const nextStyle = useRef(0);
  const currentStyle = useRef(0);

  // 아티스트 카드가 active인 동안 스타일을 일정 간격으로 바꿈
  useEffect(() => {
    if (!active || !artist) return undefined;

    const initial = nextStyle.current;
    currentStyle.current = initial;
    setStyleIndex(initial);

    if (prefersReducedMotion()) return undefined;

    let leaveTimer;
    const interval = window.setInterval(() => {
      // 이전 스타일은 잠깐 남겨 두고 서서히 사라지게, 새 스타일은 바로 시작
      setLeaving(STYLES[currentStyle.current]);
      currentStyle.current = (currentStyle.current + 1) % STYLES.length;
      setStyleIndex(currentStyle.current);
      window.clearTimeout(leaveTimer);
      leaveTimer = window.setTimeout(() => setLeaving(null), LEAVE_MS);
    }, STYLE_MS);

    return () => {
      window.clearInterval(interval);
      window.clearTimeout(leaveTimer);
      setLeaving(null);
    };
  }, [active, artist]);

  // 카드에 들어왔을 때만 잠깐 기다림 (스타일이 바뀔 때는 기다리지 않아 끊기지 않음)
  useEffect(() => {
    setReady(false);
    if (!active) return undefined;

    const timeout = window.setTimeout(() => setReady(true), READY_MS);
    return () => window.clearTimeout(timeout);
  }, [active]);

  function stop() {
    if (artist) nextStyle.current = (currentStyle.current + 1) % STYLES.length;
    setActive(false);
  }

  const style = artist && active && ready ? STYLES[styleIndex] : '';
  const titleId = `signup-role-${role}-title`;

  return (
    <article
      className={`signup-role-card ${role} ${active ? 'is-active' : ''}`}
      data-style={style}
      aria-labelledby={titleId}
      onMouseEnter={() => {
        pointerInside.current = true;
        setActive(true);
      }}
      onMouseLeave={(event) => {
        pointerInside.current = false;
        if (!event.currentTarget.contains(document.activeElement)) stop();
      }}
      onFocus={() => setActive(true)}
      onBlur={(event) => {
        if (!pointerInside.current && !event.currentTarget.contains(event.relatedTarget)) stop();
      }}
    >
      <div className="momo-stage">
        <Momo role={role} active={active && ready} style={style} leavingStyle={leaving} />
      </div>

      <span className="role-eyebrow">{artist ? 'ARTIST' : 'ORGANIZER'}</span>

      <h2 id={titleId}>{artist ? '아티스트로 시작하기' : '행사 관계자로 시작하기'}</h2>
      <p>
        {artist ? (
          <>
            나의 음악을 소개하고
            <br />
            함께할 새로운 무대를 찾아요.
          </>
        ) : (
          <>
            행사의 분위기에 어울리는
            <br />
            아티스트를 만나고 공연을 준비해요.
          </>
        )}
      </p>

      <button type="button" className="role-signup-button" onClick={() => onSelect(role)}>
        <span>{artist ? '아티스트로 가입하기' : '행사 관계자로 가입하기'}</span>
        <span aria-hidden="true">↗</span>
      </button>
    </article>
  );
}

// onSelect(role): 'artist' | 'organizer', onBack: 뒤로 가기, onLogin: 로그인 화면으로
export default function SignupRoleSelect({ onSelect, onBack, onLogin }) {
  // 로그인 화면처럼 배경을 화면 양끝까지 채우고 푸터를 숨김 (login-backdrop.css)
  useEffect(() => {
    document.body.classList.add('login-full-bleed');
    return () => document.body.classList.remove('login-full-bleed');
  }, []);

  return (
    <section
      className="signup-role-screen"
      aria-labelledby="signup-role-title"
      style={{ '--signup-role-bg': `url(${stageBackground})` }}
    >
      <button type="button" className="role-back" onClick={onBack}>
        ← 로그인으로 돌아가기
      </button>

      <header className="signup-role-heading">
        <span>YOUR NEXT STAGE</span>
        <h1 id="signup-role-title">어떤 역할로 가입할까요?</h1>
        <p>역할에 따라 가입 후 이용하는 화면과 마이페이지가 달라져요.</p>
      </header>

      <div className="signup-role-grid">
        <RoleCard role="artist" onSelect={onSelect} />
        <RoleCard role="organizer" onSelect={onSelect} />
      </div>

      <p className="role-login">
        이미 회원이신가요?
        <button type="button" onClick={onLogin}>
          로그인할래!
        </button>
      </p>
    </section>
  );
}

# 할래말래 · Frontend

> 음악과 무대, 서로에게 맞는 연결.
> 행사 관계자와 아티스트를 조건 기반으로 연결해 주는 졸업 작품(캡스톤) 웹 서비스의 **프런트엔드**입니다.

현재는 **체험판(MVP)** 단계입니다. 서버·실제 로그인·본인 인증은 아직 연결되지 않았고, 데이터는 브라우저(localStorage)에만 저장됩니다.

---

## 목차

1. [주요 화면](#주요-화면)
2. [기술 스택](#기술-스택)
3. [시작하기](#시작하기)
4. [폴더 구조](#폴더-구조)
5. [코드 흐름](#코드-흐름)
6. [작업 규칙](#작업-규칙)
7. [현재 상태와 남은 작업](#현재-상태와-남은-작업)

---

## 주요 화면

| 화면 | 설명 | 코드 위치 |
| --- | --- | --- |
| 홈 | 서비스 소개, CLAP 설명, 이용 순서 | `src/features/home/` |
| 로그인 | 아티스트(아이디·비밀번호) / 행사 관계자(휴대폰 인증) 로그인. 왼쪽 **CD를 누르면 회전하며 역할이 전환**됩니다. | `src/features/auth/components/` |
| 회원가입 | 역할 선택 → 역할별 가입 → 가입 완료 | `src/features/auth/pages/` |
| 아티스트 매칭 (행사 관계자 전용) | 행사 조건(날짜·지역·예산·장르·분위기 등)을 입력하면 조건에 맞는 아티스트를 점수순으로 TOP 5 추천 | `src/features/matching/` |
| 자유게시판 | 모집·자유 글 작성, 검색, 수정, 삭제 | `src/features/board/` |
| 마이페이지 | 역할별 프로필, 일정, 관심 아티스트 (로그인 필요) | `src/features/my-page/` |

공통: 라이트/다크 모드, 키보드 접근(본문 바로가기·포커스 표시), 움직임 줄이기 설정 시 애니메이션 끔.

## 기술 스택

| 구분 | 사용 |
| --- | --- |
| UI | React 19 |
| 빌드·개발 서버 | Vite 8 (`@vitejs/plugin-react`) |
| 코드 검사 | ESLint 10 (react-hooks, react-refresh 규칙) |
| 테스트 | Node 내장 테스트 러너 (`node --test`) |
| 스타일 | 일반 CSS (기능별 CSS 파일 + `src/app.css` 공통 스타일) |
| 저장 | 브라우저 localStorage (체험용) |

라우터·상태관리·UI 라이브러리는 사용하지 않습니다. 화면 전환은 `app.jsx`의 `page` 상태로 처리합니다.

## 시작하기

### 준비물

- **Node.js 20.19 이상** (22 LTS 권장) — Vite 8 요구 사항
- npm (Node.js에 포함)

### 설치와 실행

```sh
# 1. 저장소 받기
git clone https://github.com/nsuCapstoneTeam/NSU_CAPSTONE.git
cd NSU_CAPSTONE/frontend   # 저장소 안의 프런트엔드 폴더로 이동

# 2. 패키지 설치 (package-lock.json 기준으로 동일한 버전 설치)
npm ci

# 3. 개발 서버 실행 → 터미널에 나오는 주소(기본 http://localhost:5173)로 접속
npm run dev
```

### 자주 쓰는 명령

| 명령 | 용도 |
| --- | --- |
| `npm run dev` | 개발 서버 실행 (저장하면 화면 자동 갱신) |
| `npm run build` | 배포용 파일을 `dist/`에 생성 |
| `npm run preview` | 빌드 결과를 로컬에서 미리 보기 |
| `npm run lint` | ESLint 코드 검사 |
| `node --test src/features/matching/matching.test.js` | 매칭 로직 테스트 |

> 체험 데이터를 처음 상태로 되돌리려면 브라우저 개발자 도구 → Application → Local Storage에서 `hm-mvp-`로 시작하는 항목과 `hallaemallae-theme`를 지우세요.

## 폴더 구조

```text
frontend/
├─ DESIGN.md                  # 디자인 규칙 (색·글꼴·모양·애니메이션) — 새 화면 만들기 전에 읽기
├─ index.html                 # 진입 HTML
├─ package.json               # 스크립트·의존성
├─ vite.config.js             # Vite 설정
├─ eslint.config.js           # ESLint 설정
├─ public/                    # 그대로 복사되는 정적 파일 (favicon 등)
└─ src/
   ├─ main.jsx                # React 시작점
   ├─ app.jsx                 # 헤더·푸터, 화면 전환, 공유 상태
   ├─ app.css                 # 전체 색상·레이아웃·다크 모드
   ├─ code-guide.md           # 상세 코드 안내 (파일별 역할·이름 규칙)
   ├─ assets/                 # 로고·이미지
   ├─ components/common/      # 여러 화면에서 쓰는 공통 컴포넌트
   │  └─ icon · page-heading · score-bar · select · tag
   ├─ data/demo-data.js       # 체험용 초기 프로필·게시글
   ├─ hooks/use-stored-state.js     # React 상태 + localStorage 연결
   ├─ storage/local-demo-store.js   # 허용된 키만 읽고 쓰는 저장 모듈
   ├─ utils/                  # 작은 도우미 함수
   └─ features/               # 기능별 폴더 (화면 + 전용 컴포넌트 + CSS)
      ├─ home/
      ├─ auth/
      │  ├─ components/       # 로그인, 역할 전환 CD, 로그인 애니메이션
      │  └─ pages/            # 회원가입, 가입 완료, 로그인 필요 안내
      ├─ matching/            # 매칭 화면, 계산 로직, 테스트
      ├─ board/
      └─ my-page/
```

새 기능은 `src/features/<기능-이름>/` 폴더를 만들고 그 안에 화면(`*-page.jsx`), 전용 컴포넌트, CSS를 함께 두세요. 두 개 이상의 기능에서 쓰는 것만 `src/components/common/`으로 옮깁니다.

## 코드 흐름

```text
index.html → src/main.jsx → App(app.jsx) → page 상태에 맞는 화면
```

- **화면 전환**: `App`의 `navigate('match')`처럼 페이지 키를 바꿉니다. (`home`, `match`, `board`, `mypage`, `artist-login`, `organizer-login`, `signup-role`, `artist-signup`, `organizer-signup`, `signup-complete`, `auth-required`)
- **매칭**: `Matching` → `validateEvent` → `matchArtists` → `checkArtist`(필수 조건 필터) → `scoreArtist`(점수) → `ArtistResult`(카드 표시)
- **저장**: `App` → `useStoredState` → `local-demo-store` → localStorage
- **로그인 역할 전환**: `RoleCd`(CD) 클릭 → 회전 애니메이션 → `onChangeRole` → `App`이 `artist-login` ↔ `organizer-login` 전환

파일별 상세 설명은 [`src/code-guide.md`](src/code-guide.md)를 참고하세요.

화면 디자인 규칙(색·글꼴·버튼·로그인 화면 구성 등)은 [`DESIGN.md`](DESIGN.md)에 정리되어 있습니다. AI 코딩 도구로 화면을 만들 때도 이 파일을 먼저 읽게 하세요.

## 작업 규칙

### 이름

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 파일·폴더 | 소문자 kebab-case | `role-cd.jsx`, `login-motion.css` |
| React 컴포넌트 | PascalCase | `RoleCd`, `ArtistLogin` |
| 훅 | `use` + camelCase | `useStoredState` |
| 변수·함수 | camelCase | `matchArtists` |
| 상수 | UPPER_SNAKE_CASE | `DEFAULT_EVENT` |
| 테스트 | 대상 이름 + `.test.js` | `matching.test.js` |

### 작업 흐름 (Linear 기준)

팀 규칙 원문: Linear 문서 「NSU_CAPSTONE Agent 운영 규칙」, 「[SSOT] 아티스트-행사 매칭 플랫폼 MVP 요구사항」(v1.3)

```text
Requirement(요구사항 ID) → Linear Issue → 작업 Branch → GitHub Pull Request → Squash Merge
```

- **Issue 상태는 3개만 사용**: `Todo` → `In Progress`(실제 개발 시작 시) → `Done`(완료 확인 시)
- **GitHub Issue를 따로 만들지 않기**: Linear ↔ GitHub Issues가 자동 동기화됩니다.
- **브랜치 이름**: Linear Issue에 표시되는 git branch name 사용을 권장합니다(Issue와 자동 연결됨). 예) `loveace199/nsu-38-아티스트-필수-가입-화면-구현` — 팀 확정 규칙은 `docs/협업-가이드`를 확인하세요.
- **Pull Request에 적을 것**: 관련 Linear Issue, 관련 Requirement ID(예: `AUTH-008`), 요구사항 변경 여부, 문서 영향 여부
- **정책은 코드에서 먼저 정하지 않기**: 구현 중 새 정책이 필요하면 Slack `#dev` 논의 → Human Confirm → Linear Requirements 갱신 후 구현합니다.
- **규칙이 서로 다를 때 우선순위**: 최신 Slack Human Decision → Linear Requirements → GitHub docs → Linear Issue
- 단순 오타·문서 정리(`docs`/`chore`)에는 가짜 Issue나 Requirement ID를 만들지 않습니다.
- 상세 협업 규칙은 GitHub `docs/협업-가이드/README.md`를 따릅니다.

### 커밋 메시지

- `종류(범위): 설명` — 예) `feat(auth): 로그인 역할 전환 CD 추가`
- 종류: `feat` 기능 · `fix` 오류 수정 · `refactor` 구조 개선 · `style` 서식 · `docs` 문서 · `test` 테스트 · `chore` 기타

### 프런트엔드가 지켜야 할 요구사항

| 규칙 | 요구사항 ID | 이 프로젝트에서 |
| --- | --- | --- |
| 사용자는 아티스트(`ARTIST`)와 행사 관계자(`EVENT_PARTNER`) 두 종류만, 한 계정은 한 역할 | USER-003, AUTH-005 | 코드의 `role`: `'artist'` / `'organizer'`(= EVENT_PARTNER) |
| 가입 공통: 18세 이상 확인, 이용약관·개인정보 동의, 이메일, 휴대전화번호 | AUTH-006 | 가입 화면 동의 항목 |
| 아티스트 가입 화면에서 YouTube·SoundCloud 연결, 작업물 선택·업로드, 저작권 검증 제외 → 가입 후 마이페이지에서 | AUTH-008, NSU-38 | 가입 화면 하단 안내 |
| 아티스트 매칭·출연료 열람은 행사 관계자 기능, 출연료는 행사 관계자에게만 공개 | EVT-041, ART-028 | 매칭 화면은 행사 관계자 로그인 시에만 표시 |
| 매칭: 필수 조건은 PASS/FAIL로만, 점수는 항목 평균, TOP 5 + 항목별 점수 + 추천 이유 + 1·2위 비교 | AI-MATCH-051~054 | `matching.js` |
| AI가 아티스트 실력을 평가하는 표현 금지 (예: "노래 실력 68점") | AI-040 | 점수는 행사 적합도만 표시 |
| CLAP 연결 전에는 "AI 분석 중"처럼 오해할 문구 금지 | NSU-25 | 샘플 계산임을 화면에 표시 |
| 비공개 행사를 프런트엔드에서만 숨기는 구조 금지 → 백엔드 권한 검사 필수 | SEC-100, EVT-044 | 행사 기능 구현 시 적용 |
| 필요 없는 개인정보 수집·저장 금지 | SEC-120 | 비밀번호·인증번호는 저장하지 않음 |
| 신뢰 정보(저작권 증빙 등)와 매칭 점수는 분리, 인증을 점수 가산점으로 쓰지 않음 | TRUST-143, TRUST-152 | 가입 화면 안내 문구 |
| 계약·결제·정산·리뷰는 MVP 제외 | MVP-128 | 푸터에 "결제·정산 미제공" |
| 움직임 줄이기 설정 존중, 라이트·다크 모드 가독성, 키보드 조작 | NSU-25 | 모든 애니메이션에 적용 |

### 올리기 전 확인

```sh
npm run lint
node --test src/features/matching/matching.test.js
npm run build
```

세 가지가 모두 통과한 뒤 Pull Request를 만들어 주세요. `node_modules/`, `dist/`는 `.gitignore`에 포함되어 있어 올라가지 않습니다.

## 현재 상태와 남은 작업

**체험판에서 동작하는 것**

- 매칭 추천(샘플 아티스트 데이터 기준), 게시판 CRUD, 마이페이지 일정·관심 목록
- 로그인·회원가입 화면 흐름 (입력값은 서버로 전송하지 않음)

**아직 연결되지 않은 것**

- [ ] 백엔드 API·DB 연동 (현재 localStorage)
- [ ] 실제 로그인·간편 로그인(카카오·네이버·구글)·휴대폰 본인 인증
- [ ] CLAP 연동
- [ ] 결제·정산 (MVP 범위 밖)

# 할래말래 코드 안내

## 화면에서 코드를 찾는 방법

| 바꾸려는 부분 | 파일 / 함수 |
| --- | --- |
| 앱 시작 | `src/main.jsx` |
| 메뉴, 로고, 화면 전환, 테마, 전체 상태 | `src/app.jsx` → `App` |
| 홈 소개와 이용 순서 | `src/features/home/home-page.jsx` |
| 홈 CLAP 설명·경량 시각 요소·비교 그래픽 | `src/features/home/components/` |
| 로그인·역할 선택 | `src/features/auth/components/` |
| 역할별 회원가입·가입 완료·접근 안내 | `src/features/auth/pages/` |
| 행사 조건 입력과 추천 결과 | `src/features/matching/matching-page.jsx` |
| 매칭 계산·샘플 데이터 | `src/features/matching/matching.js` |
| 아티스트 추천 카드 | `src/features/matching/components/artist-result.jsx` |
| 역할별 프로필·일정·관심 목록 | `src/features/my-page/my-page.jsx` |
| 게시글 작성·검색·수정·삭제 | `src/features/board/board-page.jsx` |
| 공통 아이콘·제목·선택 상자·태그·점수 막대 | `src/components/common/` 아래 각각의 JSX 파일 |
| 전체 색상·레이아웃·다크 모드 | `src/app.css` |
| 샘플 후보, 필수 조건, 점수, 추천 이유 | `src/features/matching/matching.js` |
| 매칭 로직 테스트 | `src/features/matching/matching.test.js` |
| 초기 체험 프로필과 게시글 | `src/data/demo-data.js` |
| React 상태와 브라우저 저장 연결 | `src/hooks/use-stored-state.js` |
| 허용된 체험 데이터만 읽고 쓰는 저장 모듈 | `src/storage/local-demo-store.js` |

기능별 코드는 `src/features` 폴더에서 확인하세요. 각 기능 폴더 안에 화면, 전용 컴포넌트, 스타일을 함께 둡니다. `app.jsx`는 화면 전환·공유 상태·공통 헤더와 푸터를 담당하고, 여러 기능에서 함께 쓰는 아이콘과 입력 컴포넌트만 `src/components/common`에 둡니다.

## 실행 흐름

`index.html` → `src/main.jsx` → `App` → 선택한 화면

매칭: `Matching` → `validateEvent` → `matchArtists` → `checkArtist`로 필터링 → `scoreArtist`로 점수 계산 → `ArtistResult` 표시.

저장: `App` → `useStoredState` → `local-demo-store` → 브라우저 localStorage.

현재 CLAP·본인인증·서버 저장은 연결 전입니다. 점수는 샘플 조건 계산이며, 파일 정리는 계산식이나 저장 키를 변경하지 않습니다. Three.js는 초기 화면 용량을 줄이기 위해 제거했고 CLAP 영역은 CSS 기반으로 표시합니다.

## 이름 규칙

사용자 정의 파일과 폴더 이름은 소문자와 대시를 사용하고, 이름만으로 역할을 알 수 있게 작성합니다. React 컴포넌트와 훅의 코드 식별자는 React·JavaScript 관례를 유지합니다.

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 사용자 정의 파일·폴더 | lowercase kebab-case | `clap-hero.jsx`, `clap-hero.css` |
| React 컴포넌트 식별자 | PascalCase | `ClapHero`, `ArtistLogin` |
| React 훅 파일 / 식별자 | kebab-case / use + camelCase | `use-stored-state.js` / `useStoredState` |
| 설정·도구 표준 파일 | 도구가 요구하는 이름 유지 | `README.md`, `package.json`, `vite.config.js` |
| 변수·함수 | camelCase | `matchArtists`, `toggleSaved` |
| 상수 | UPPER_SNAKE_CASE | `DEFAULT_EVENT` |
| 테스트 | 대상 이름 + .test.js | `matching.test.js` |
| 브랜치 | 종류/짧은-영문-설명 | `refactor/organize-frontend` |
| 커밋 | 종류(범위): 설명 | `refactor(frontend): 역할별 파일 구조 정리` |

커밋 종류: `feat` 기능 추가, `fix` 오류 수정, `refactor` 동작 유지 구조 개선, `docs` 문서, `test` 테스트, `style` 코드 서식, `chore` 기타 관리. 화면 디자인 변경은 성격에 따라 feat 또는 fix를 사용합니다.

이번 변경의 커밋 메시지 예: `refactor(frontend): 파일 구조 정리 및 코드 안내 추가`.
브랜치 생성, 커밋, GitHub 업로드는 이 정리 작업에 포함하지 않습니다.

## 경로 변경표

| 이전 | 이후 |
| --- | --- |
| 홈 관련 파일 | `src/features/home/` |
| 로그인·회원가입 관련 파일 | `src/features/auth/` |
| 매칭 관련 파일 | `src/features/matching/` |
| `src/matching.js`, `src/matching.test.js` | `src/features/matching/` 아래 같은 이름 |
| `src/data.js` | `src/data/demo-data.js` |
| `src/useStoredState.js` | `src/hooks/use-stored-state.js` |
| `src/localDemoStore.js` | `src/storage/local-demo-store.js` |

기존 `src/index.css`, `src/theme.css`는 현재 진입점에서 불러오지 않는 이전 스타일 파일입니다. 확정 로고는 `src/assets/hallaemallae-logo.png`에서 관리합니다.

## 확인 명령

프로젝트 루트에서 실행합니다.

```sh
node --test src/features/matching/matching.test.js
npm run lint
npm run build
```


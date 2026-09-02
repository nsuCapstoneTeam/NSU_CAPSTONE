# GitHub 접근 권한 체크리스트

## Repository Owner (`seokyun-kang`)

- [ ] 개발자 3명의 GitHub username을 받음
- [ ] Settings → Collaborators에서 3명 초대
- [ ] 모든 개발자가 초대를 수락했는지 확인
- [ ] `main` 기본 브랜치 존재
- [ ] Squash Merge 활성화
- [ ] Merge commit 비활성화
- [ ] Rebase merge 비활성화
- [ ] Merge 후 branch 자동 삭제 활성화
- [ ] `main` Ruleset 생성
- [ ] PR 필수
- [ ] 승인 1명 필수
- [ ] force push 차단
- [ ] CODEOWNERS 실제 GitHub username으로 교체
- [ ] 추후 CodeRabbit/Linear/Claude App은 이 Repository만 허용

## 각 개발자

- [ ] Collaborator 초대 수락
- [ ] SSH 또는 HTTPS 인증 설정
- [ ] `ssh -T git@github.com` 또는 GitHub CLI 인증 확인
- [ ] Repository clone 성공
- [ ] `git pull origin main` 성공
- [ ] 작업 branch push 성공
- [ ] main 직접 push하지 않음
- [ ] SSH private key / PAT를 팀원과 공유하지 않음

## Claude Code 사용 전

- [ ] 개발자 본인 계정으로 branch push 가능
- [ ] `.env` / secret이 `.gitignore`에 포함됨
- [ ] Claude Code도 main 직접 push 금지
- [ ] 초기에는 push/PR 전 사람이 diff 확인

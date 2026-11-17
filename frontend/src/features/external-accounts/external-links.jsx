import { displayNameOf, verificationText } from './account-rules.js';
import { safeLink, safeThumbnail } from './safe-link.js';
import './external-accounts.css';

// 아티스트가 연결한 외부 계정과 고른 작업물을 "원래 사이트로 가는 링크"로 보여 주는 표시 컴포넌트 (API 06 §6)
// - 재생기는 넣지 않습니다. 재생은 각 사이트에서 합니다(외부 링크 ADR 1번).
// - 서버가 준 링크·이미지·글자는 믿지 않는 입력입니다. 글자는 React가 그대로 글자로만 그리고,
//   주소는 safe-link.js로 한 번 더 거릅니다.
// - 출처 표기: YouTube는 YouTube 표시, Spotify는 Spotify 표시와 원래 페이지 링크를 둡니다.
//   플랫폼 공식 로고 파일은 라이선스 때문에 아직 넣지 않았고 글자로 표기합니다(runbook·PR 설명 참고).
// accounts: API 06 §6.2 계정 목록. 비어 있으면 아무것도 그리지 않습니다.
// 이 화면을 EVENT_PARTNER의 아티스트 정보에 붙이는 일은 NSU-90에서 합니다.

const ATTRIBUTION = {
  YOUTUBE: { brand: 'YouTube', open: 'YouTube에서 보기' },
  SPOTIFY: { brand: 'Spotify', open: 'Spotify에서 열기' },
};

const WORK_FALLBACK_TITLE = 'YouTube 영상';

function Thumbnail({ url }) {
  const src = safeThumbnail(url);
  if (!src) return null;
  return (
    <img
      className="ext-thumb"
      src={src}
      alt=""
      loading="lazy"
      referrerPolicy="no-referrer"
    />
  );
}

function ExternalLink({ provider, url, children }) {
  const href = safeLink(provider, url);
  if (!href) return <span>{children}</span>;
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
    >
      {children}
    </a>
  );
}

export default function ExternalLinks({ accounts }) {
  if (!Array.isArray(accounts) || accounts.length === 0) return null;
  return (
    <ul
      className="ext-links"
      aria-label="연결한 외부 계정"
    >
      {accounts.map((account) => {
        const brand = ATTRIBUTION[account.provider];
        return (
          <li
            key={account.externalAccountId}
            className="ext-account"
          >
            <div className="ext-account-head">
              <Thumbnail url={account.thumbnailUrl} />
              <div>
                <strong>
                  <ExternalLink
                    provider={account.provider}
                    url={account.profileUrl}
                  >
                    {displayNameOf(account)}
                  </ExternalLink>
                </strong>
                <span className="ext-status">{verificationText(account)}</span>
              </div>
              {brand && <span className={`ext-brand ext-brand-${account.provider.toLowerCase()}`}>{brand.brand}</span>}
            </div>
            {account.works?.length > 0 && (
              <ul
                className="ext-works"
                aria-label={`${displayNameOf(account)}의 작업물`}
              >
                {account.works.map((work) => (
                  <li key={work.workId}>
                    <Thumbnail url={work.thumbnailUrl} />
                    <ExternalLink
                      provider={account.provider}
                      url={work.url}
                    >
                      {work.title ?? WORK_FALLBACK_TITLE}
                    </ExternalLink>
                  </li>
                ))}
              </ul>
            )}
            {brand && (
              <p className="ext-source">
                <ExternalLink
                  provider={account.provider}
                  url={account.profileUrl}
                >
                  {brand.open}
                </ExternalLink>
              </p>
            )}
          </li>
        );
      })}
    </ul>
  );
}

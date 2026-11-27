// 지도 경계 출처 (NSU-117). CC BY 4.0은 원본과 라이선스를 가리키는 링크를 함께 적어야 해서 링크로 보여 줍니다.
// short: 작은 지도 아래처럼 자리가 좁은 곳에서 짧게
const SOURCE_URL = 'https://github.com/vuski/admdongkor';
const LICENSE_URL = 'https://creativecommons.org/licenses/by/4.0/deed.ko';

function Link({ href, children }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noreferrer"
    >
      {children}
    </a>
  );
}

export default function MapSource({ className, short = false }) {
  if (short) {
    return (
      <span className={className}>
        지도 경계: 통계청 SGIS, <Link href={SOURCE_URL}>vuski/admdongkor</Link> (<Link href={LICENSE_URL}>CC BY 4.0</Link>)
      </span>
    );
  }
  return (
    <p className={className}>
      지도 경계: 통계청 SGIS 행정동 경계(공공누리 제1유형)를 <Link href={SOURCE_URL}>vuski/admdongkor</Link>가 가공한
      자료(<Link href={LICENSE_URL}>CC BY 4.0</Link>)를 합쳐서 그렸어요.
    </p>
  );
}

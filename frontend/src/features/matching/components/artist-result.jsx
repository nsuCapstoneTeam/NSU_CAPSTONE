import { explainLead } from '../matching.js';
import Icon from '../../../components/common/icon.jsx';
import Tag from '../../../components/common/tag.jsx';
import ScoreBar from '../../../components/common/score-bar.jsx';
import { scoreText } from '../../../utils/format-score.js';

// 추천 결과 카드 한 장. index는 순위(0이 1위), next는 바로 다음 순위 후보(1위 설명용)
export default function ArtistResult({
  artist,
  index,
  next,
  saved,
  toggleSaved,
}) {
  return (
    <article className={`result-card ${index === 0 ? 'top-result' : ''}`}>
      {/* 카드 윗줄: 순위 아바타 · 이름/지역/출연료 · 종합 점수 · 관심 저장 버튼 */}
      <div className="result-summary">
        <div className={`artist-avatar tone-${artist.color}`}>
          <Icon
            name="music"
            size={27}
          />
          <span>{String(index + 1).padStart(2, '0')}</span>
        </div>
        <div className="artist-identity">
          <div className="tags">
            <Tag>{artist.genres.join(' · ')}</Tag>
            <Tag tone="subtle">가상 아티스트</Tag>
          </div>
          <h3>{artist.name}</h3>
          <p>
            {artist.regions.join(' · ')} <span>·</span> 기준 출연료{' '}
            <b>{artist.fee}만 원</b>
          </p>
        </div>
        <div className="result-score">
          <small>조건 적합도</small>
          <strong>
            {scoreText(artist.score)}
            <span>/100</span>
          </strong>
        </div>
        <button
          className={`icon-button ${saved ? 'selected' : ''}`}
          aria-label={`${artist.name} 관심 저장`}
          aria-pressed={saved}
          onClick={() => toggleSaved(artist.id)}
        >
          <Icon name="bookmark" />
        </button>
      </div>
      {/* 항목별 점수 막대 */}
      <div className="result-metrics">
        {artist.metrics.map((m) => (
          <ScoreBar
            key={m.key}
            {...m}
          />
        ))}
      </div>
      {/* 1위 카드에만 2위보다 앞선 이유 표시 */}
      {index === 0 && (
        <div className="lead-reason">
          <Icon
            name="check"
            size={18}
          />
          <p>
            <strong>왜 첫 번째로 추천했나요?</strong>
            <br />
            {explainLead(artist, next)}
          </p>
        </div>
      )}
      {/* 펼쳐 보기: 필수 조건 통과 내역과 점수 계산 근거 */}
      <details>
        <summary>
          필수 조건과 계산 근거 보기 <span>＋</span>
        </summary>
        <div className="detail-content">
          <h4>필수 조건 · 모두 통과</h4>
          <div className="checks">
            {artist.checks.map((c) => (
              <div key={c.label}>
                <Icon
                  name="check"
                  size={15}
                />
                <p>
                  <b>{c.label}</b>
                  <small>{c.detail}</small>
                </p>
              </div>
            ))}
          </div>
          <h4>점수 산식</h4>
          {artist.metrics.map((m) => (
            <p key={m.key}>
              <b>
                {m.label} {scoreText(m.score)}점
              </b>
              <br />
              {m.reason}
            </p>
          ))}
          {/* 종합 점수 = 항목 점수 평균 */}
          <div className="formula">
            ({artist.metrics.map((m) => m.score).join(' + ')}) ÷{' '}
            {artist.metrics.length} = {scoreText(artist.score)}점
          </div>
          <p>
            검증 상태·사업자 여부는 점수에 더하지 않습니다. 오디오 파일이
            연결되지 않아 CLAP 분석 및 실제 음원 재생은 제공하지 않습니다.
          </p>
          <div className="tags">
            <Tag>샘플 신원 확인</Tag>
            <Tag>샘플 작업물 권리 확인</Tag>
            {artist.business && <Tag>샘플 사업자 정보 확인</Tag>}
          </div>
        </div>
      </details>
    </article>
  );
}

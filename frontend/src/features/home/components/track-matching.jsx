import { useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import StageCd from './stage-cd.jsx';
import TrackMatchingDetail from './track-matching-detail.jsx';
import { MATCHING_SAMPLE as SAMPLE, beatCount, tempoPosition } from '../matching-sample.js';
import { beatTracePath } from '../../matching/song-results.js';

// 원형 그래프의 세 조각. 카드·상세 화면과 같은 key로 연결됩니다. (모양·위치는 home-stage.css의 .stage-pie-piece.is-*)
const PIECES = [
  { key: 'similarity', label: '음악 유사도' },
  { key: 'tempo', label: '템포' },
  { key: 'rhythm', label: '리듬' },
];

// 셋째 구간: 추천 결과 화면이 무엇을 보여 주는지 샘플 대시보드로 설명합니다.
// 음악 유사도·템포·리듬 카드나 오른쪽 그래프 조각을 누르면 그 항목의 상세 화면이 대시보드 자리에 열립니다.
export default function TrackMatching({ sectionProps, navigate }) {
  // hot: 마우스를 올리거나 키보드로 고른 항목 ('all'이면 세 조각 모두), open: 열린 상세 화면
  const [hot, setHot] = useState(null);
  const [open, setOpen] = useState(null);

  function openDetail(key) {
    setOpen(key);
    setHot(null);
  }
  function closeDetail() {
    const key = open;
    setOpen(null);
    setHot(null);
    // 상세 화면을 열었던 카드 버튼으로 포커스를 돌려줌
    setTimeout(() => document.getElementById(`home-matching-card-${key}`)?.focus({ preventScroll: true }), 0);
  }

  // 그래프 조각을 강조할 항목: '전체보기'에 마우스를 올리면 세 조각 모두,
  // 상세 화면이 열려 있으면 그 항목, 아니면 마우스·키보드로 고른 항목
  const lit = hot === 'all' ? 'all' : open || hot;

  // 카드 버튼 공통 속성: 고르면 같은 조각이 튀어나오고, 누르면 상세 화면이 열림 (label: 화면 낭독기용 이름)
  function cardProps(key, label) {
    return {
      'aria-label': `${label}, 자세히 보기`,
      id: `home-matching-card-${key}`,
      type: 'button',
      className: `stage-glass is-linked${key === 'similarity' ? ' is-similarity' : ''}${hot === key ? ' is-hot' : ''}`,
      onMouseEnter: () => setHot(key),
      onMouseLeave: () => setHot(null),
      onFocus: () => setHot(key),
      onBlur: () => setHot(null),
      onClick: () => openDetail(key),
    };
  }

  return (
    <section
      {...sectionProps}
      className="stage-track stage-track-matching"
      aria-labelledby="home-track-matching-title"
    >
      <div className="stage-matching-board">
        <div className="stage-matching-head">
          <h2
            id="home-track-matching-title"
            className="stage-track-title"
          >
            음악 유사도로 순위를 정해요
          </h2>
          <p>
            등록한 행사 설명을 AI가 해석하고, CLAP으로 곡이 얼마나 가까운지 계산해요. 카드나 그래프 조각을
            누르면 자세히 볼 수 있어요. 아래는 샘플 화면이에요.
          </p>
        </div>

        {open ? (
          <TrackMatchingDetail
            key={open}
            kind={open}
            onBack={closeDetail}
          />
        ) : (
          <div className="stage-glass-grid">
            <button {...cardProps('similarity', `음악 유사도 ${SAMPLE.similarity}`)}>
              <span className="stage-glass-label">
                음악 유사도<span className="stage-glass-more">자세히 보기</span>
              </span>
              <span className="stage-similarity">
                <span
                  className="stage-ring"
                  style={{ '--value': SAMPLE.similarity }}
                />
                <span>
                  <strong>{SAMPLE.similarity}</strong>
                  <span className="stage-glass-note">1위 곡, 순위를 정하는 유일한 값</span>
                </span>
              </span>
            </button>

            <button {...cardProps('tempo', `템포 BPM ${SAMPLE.bpm}`)}>
              <span className="stage-glass-label">
                템포<span className="stage-glass-more">자세히 보기</span>
              </span>
              <strong className="stage-glass-value">BPM {SAMPLE.bpm}</strong>
              <span
                className="stage-tempo"
                style={{ '--pos': `${tempoPosition(SAMPLE.bpm)}%` }}
              >
                <span className="stage-tempo-thumb" />
              </span>
              <span className="stage-tempo-scale">
                <span>느림</span>
                <span>보통</span>
                <span>빠름</span>
              </span>
              <span className="stage-glass-note">점수 없음, 참고 정보</span>
            </button>

            <button {...cardProps('rhythm', `리듬, 한 마디에 박 ${beatCount(SAMPLE.beats)}번`)}>
              <span className="stage-glass-label">
                리듬<span className="stage-glass-more">자세히 보기</span>
              </span>
              {/* 이 곡의 한 마디 박자를 심전도 모양 선으로 (추천 이유 슬라이드의 리듬 그래프와 같은 모양) */}
              <svg
                className="stage-beat-line"
                viewBox="0 0 320 60"
                preserveAspectRatio="none"
                aria-hidden="true"
              >
                <path
                  d={beatTracePath(SAMPLE.beats, { cellWidth: 20, bars: 1, baseline: 38, amp: 26 })}
                  vectorEffect="non-scaling-stroke"
                />
              </svg>
              <strong className="stage-glass-value is-small">한 마디에 박 {beatCount(SAMPLE.beats)}번</strong>
              <span className="stage-glass-note">이 곡의 박자, 참고 정보</span>
            </button>

            <article className="stage-glass">
              <h3 className="stage-glass-label">필수 조건</h3>
              <ul className="stage-required">
                {SAMPLE.required.map((item) => (
                  <li key={item}>
                    <Icon
                      name="check"
                      size={13}
                    />
                    {item}
                  </li>
                ))}
              </ul>
              <p className="stage-glass-note">목록의 곡은 모두 통과한 곡이에요.</p>
            </article>

            <article className="stage-glass is-results">
              <div className="stage-results-head">
                <h3 className="stage-glass-label">추천 결과 상위 10곡 (샘플)</h3>
                <span>더보기로 최대 100곡</span>
              </div>
              <ol className="stage-results">
                {SAMPLE.songs.map((item, index) => (
                  <li key={item.artist}>
                    <span className="stage-result-rank">{index + 1}</span>
                    <span
                      className="stage-result-avatar"
                      aria-hidden="true"
                    >
                      {item.artist.slice(0, 1)}
                    </span>
                    <span className="stage-result-name">
                      <b>{item.artist}</b>
                      {item.isNew && <span className="stage-tag">신규</span>}
                      <span>{item.song}</span>
                    </span>
                    <span className="stage-result-score">{item.score}</span>
                  </li>
                ))}
              </ol>
            </article>
          </div>
        )}

        <button
          className="stage-button is-primary"
          onClick={() => navigate('match')}
        >
          조건 매칭 체험하기
          <Icon
            name="arrow"
            size={16}
          />
        </button>
      </div>

      {/* 오른쪽 원형 그래프: 결과 카드가 보여 주는 세 가지 정보. 크기는 같지만 순위에는 음악 유사도만 씁니다.
          조각을 누르면 상세 화면, 가운데를 누르면 전체보기(대시보드)로 돌아갑니다.
          키보드 사용자는 왼쪽 카드 버튼과 '추천 결과로 돌아가기'로 같은 일을 할 수 있어 그래프는 화면 낭독기에서 숨깁니다. */}
      <div
        className={`stage-pie-wrap${lit ? ' has-hot' : ''}`}
        aria-hidden="true"
      >
        <StageCd className="stage-pie">
          {PIECES.map((piece) => (
            <div
              key={piece.key}
              className={`stage-pie-piece is-${piece.key}${lit === piece.key || lit === 'all' ? ' is-hot' : ''}`}
            >
              <span
                className="stage-pie-fill"
                onMouseEnter={() => setHot(piece.key)}
                onMouseLeave={() => setHot(null)}
                onClick={() => openDetail(piece.key)}
              />
              <span className="stage-pie-tag">{piece.label}</span>
            </div>
          ))}
          {/* 상세 화면이 열려 있을 때만 보이는 가운데 '전체보기' 버튼 */}
          {open && (
            <button
              className="stage-pie-hub"
              type="button"
              tabIndex={-1}
              onMouseEnter={() => setHot('all')}
              onMouseLeave={() => setHot(null)}
              onClick={closeDetail}
            >
              전체보기
            </button>
          )}
        </StageCd>
      </div>
    </section>
  );
}

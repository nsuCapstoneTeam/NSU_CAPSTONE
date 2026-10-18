// 홈 무대의 CD. CSS 원과 그라데이션으로만 그립니다. (DESIGN.md 10.7: 원 모양 장식은 CSS 허용)
// photo: CD 면에 비치는 무대 사진, label: 가운데 라벨 글자, children: 라벨 위에 얹을 내용(매칭 구간의 원형 그래프 등)
export default function StageCd({ photo, label = '할래말래', className = '', children }) {
  return (
    <div className={`stage-cd ${className}`}>
      <div className="stage-cd-disc">
        <div className="stage-cd-spin">
          <div className="stage-cd-iris" />
          {photo && (
            <div
              className="stage-cd-reflect"
              style={{ backgroundImage: `url(${photo})` }}
            />
          )}
          <div className="stage-cd-grooves" />
        </div>
        <div className="stage-cd-sheen" />
      </div>
      {children}
      <div className="stage-cd-label">
        <span className="stage-cd-arc" />
        <span className="stage-cd-name">{label}</span>
        <span className="stage-cd-hub" />
      </div>
    </div>
  );
}

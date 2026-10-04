// 각 화면 상단 제목 영역: 작은 머리글(eyebrow) + 제목 + 설명
export default function PageHeading({ eyebrow, title, description }) {
  return (
    <div className="page-heading">
      <p className="eyebrow">{eyebrow}</p>
      <h1>{title}</h1>
      <p>{description}</p>
    </div>
  );
}

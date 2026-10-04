// 작은 꼬리표. tone으로 색상 변형 클래스를 추가합니다.
export default function Tag({ children, tone = '' }) {
  return <span className={`tag ${tone}`}>{children}</span>;
}

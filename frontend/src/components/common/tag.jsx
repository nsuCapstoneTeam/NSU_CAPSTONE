export default function Tag({ children, tone = '' }) {
  return <span className={`tag ${tone}`}>{children}</span>;
}

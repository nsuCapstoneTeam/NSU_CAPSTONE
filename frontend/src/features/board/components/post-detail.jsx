import { useEffect, useRef, useState } from 'react';
import Tag from '../../../components/common/tag.jsx';
import './post-detail.css';

// 게시글 상세 화면 (NSU-55)
// post: 보여 줄 글 (없으면 "찾을 수 없음" 안내), isOwner: 본인 글이면 수정·삭제 표시
// onBack: 목록으로, onEdit: 수정 편집기 열기, onDelete: 삭제 확정
export default function PostDetail({ post, isOwner, onBack, onEdit, onDelete }) {
  // 삭제 확인 중인지 (한 번 더 눌러야 삭제)
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  // 상세 화면이 열리면 제목으로 포커스 이동 (화면낭독기가 바로 제목을 읽도록)
  const titleRef = useRef(null);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'instant' });
    titleRef.current?.focus({ preventScroll: true });
  }, [post?.id]);

  // 지워졌거나 잘못된 글을 열었을 때
  if (!post) {
    return (
      <section className="post-detail" aria-labelledby="post-detail-title">
        <button type="button" className="text-button post-detail-back" onClick={onBack}>
          ← 목록으로
        </button>
        <div className="empty-state">
          <h2 id="post-detail-title" ref={titleRef} tabIndex={-1}>
            게시글을 찾을 수 없어요.
          </h2>
          <p>삭제되었거나 이 브라우저에 저장되지 않은 글이에요.</p>
        </div>
      </section>
    );
  }

  return (
    <article className="post-detail" aria-labelledby="post-detail-title">
      <button type="button" className="text-button post-detail-back" onClick={onBack}>
        ← 목록으로
      </button>

      {/* 머리: 분류 · 제목 · 작성자/날짜 */}
      <header className="post-detail-header">
        <Tag tone={post.type === '모집' ? '' : 'subtle'}>{post.type}</Tag>
        <h1 id="post-detail-title" ref={titleRef} tabIndex={-1}>
          {post.title}
        </h1>
        <p className="post-detail-meta">
          <span>{post.author}</span>
          <span aria-hidden="true">·</span>
          <time dateTime={post.date}>{post.date}</time>
          {!isOwner && (
            <>
              <span aria-hidden="true">·</span>
              <span>샘플 게시글</span>
            </>
          )}
        </p>
      </header>

      {/* 본문: 줄바꿈 그대로 표시 */}
      <div className="post-detail-body">
        <p>{post.body}</p>
      </div>

      {/* 본인 글일 때만 수정·삭제 */}
      {isOwner && (
        <footer className="post-detail-actions">
          <div className="button-row">
            <button type="button" className="secondary" onClick={onEdit}>
              수정
            </button>
            <button
              type="button"
              className="text-button danger"
              onClick={() => setConfirmingDelete(true)}
            >
              삭제
            </button>
          </div>
          {confirmingDelete && (
            <div className="delete-confirm" role="alert">
              <span>이 게시글을 삭제할까요?</span>
              <button type="button" className="secondary" onClick={onDelete}>
                삭제 확인
              </button>
              <button
                type="button"
                className="text-button"
                onClick={() => setConfirmingDelete(false)}
              >
                취소
              </button>
            </div>
          )}
        </footer>
      )}
    </article>
  );
}

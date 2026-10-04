import { useState } from 'react';
import Icon from '../../components/common/icon.jsx';
import PageHeading from '../../components/common/page-heading.jsx';
import Select from '../../components/common/select.jsx';
import Tag from '../../components/common/tag.jsx';

// 체험 게시판: 분류·검색 필터, 글쓰기·수정·삭제 (이 브라우저에만 저장)
// 본인(profile.id)이 쓴 글만 수정·삭제할 수 있습니다.
export default function Board({ posts, setPosts, profile, notify }) {
  // filter: 분류 탭, query: 검색어, opened: 펼친 글 id,
  // editing: 작성·수정 중인 글 (null이면 편집기 닫힘), deleteId: 삭제 확인 중인 글 id
  const [filter, setFilter] = useState('전체'),
    [query, setQuery] = useState(''),
    [opened, setOpened] = useState(null),
    [editing, setEditing] = useState(null),
    [deleteId, setDeleteId] = useState(null);
  // 분류와 검색어(제목·내용·작성자, 대소문자 무시)로 걸러낸 글 목록
  const visible = posts.filter(
    (p) =>
      (filter === '전체' || p.type === filter) &&
      `${p.title} ${p.body} ${p.author}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  // 글 저장: id가 있으면 본인 글 수정, 없으면 새 글을 맨 앞에 추가
  function save(e) {
    e.preventDefault();
    if (!editing.title.trim() || !editing.body.trim()) {
      notify('제목과 내용을 입력해 주세요.');
      return;
    }
    const item = {
      ...editing,
      title: editing.title.trim(),
      body: editing.body.trim(),
      owner: profile.id,
      author: profile.name,
    };
    const saved = setPosts(
      editing.id
        ? posts.map((p) =>
            p.id === editing.id && p.owner === profile.id ? item : p,
          )
        : [
            {
              ...item,
              id: crypto.randomUUID(),
              date: new Date().toISOString().slice(0, 10),
            },
            ...posts,
          ],
    );
    setEditing(null);
    notify(
      saved
        ? '게시글을 이 브라우저에 저장했습니다.'
        : '게시글이 화면에는 반영됐지만 브라우저 저장에 실패했습니다. 새로고침하면 사라질 수 있습니다.',
    );
  }
  return (
    <>
      <PageHeading
        eyebrow="COMMUNITY"
        title="무대 밖에서도, 함께."
        description="공연 모집부터 준비 경험까지. 자유롭게 이야기를 나누는 공간입니다."
      />
      {/* 상단 도구: 분류 탭 · 검색 · 글쓰기 */}
      <div className="board-toolbar">
        <div className="tab-buttons">
          {['전체', '모집', '자유'].map((t) => (
            <button
              key={t}
              className={filter === t ? 'chosen' : ''}
              aria-pressed={filter === t}
              onClick={() => setFilter(t)}
            >
              {t}
            </button>
          ))}
        </div>
        <label className="search-input">
          <Icon
            name="search"
            size={18}
          />
          <input
            aria-label="게시글 검색"
            placeholder="제목, 내용, 작성자 검색"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </label>
        <button
          className="primary"
          onClick={() => setEditing({ title: '', body: '', type: '모집' })}
        >
          글쓰기 ＋
        </button>
      </div>
      <p className="field-note">
        같은 브라우저에서 사용하는 체험 게시판입니다. 모집 글의 행사·작성자
        검증이나 실제 전송은 제공하지 않습니다.
      </p>
      {/* 글쓰기·수정 편집기 */}
      {editing && (
        <form
          className="panel post-editor stacked-form"
          onSubmit={save}
        >
          <h2>{editing.id ? '게시글 수정' : '새 게시글'}</h2>
          <Select
            label="분류"
            value={editing.type}
            options={['모집', '자유']}
            onChange={(type) => setEditing({ ...editing, type })}
          />
          <label>
            제목
            <input
              required
              maxLength={100}
              value={editing.title}
              onChange={(e) =>
                setEditing({ ...editing, title: e.target.value })
              }
            />
          </label>
          <label>
            내용
            <textarea
              required
              maxLength={3000}
              rows={6}
              value={editing.body}
              onChange={(e) => setEditing({ ...editing, body: e.target.value })}
            />
          </label>
          <div className="button-row">
            <button
              className="primary"
              type="submit"
            >
              {editing.id ? '수정 저장' : '게시글 저장'}
            </button>
            <button
              className="secondary"
              type="button"
              onClick={() => setEditing(null)}
            >
              취소
            </button>
          </div>
        </form>
      )}
      {/* 게시글 목록: 제목을 누르면 내용이 펼쳐짐 */}
      <div className="board-list">
        {visible.map((p) => (
          <article
            key={p.id}
            className="post-item"
          >
            <button
              className="post-toggle"
              onClick={() => setOpened(opened === p.id ? null : p.id)}
              aria-expanded={opened === p.id}
            >
              <Tag tone={p.type === '모집' ? '' : 'subtle'}>{p.type}</Tag>
              <div>
                <h3>{p.title}</h3>
                <p>
                  {p.author} · {p.date}
                  {p.owner !== profile.id ? ' · 샘플 게시글' : ''}
                </p>
              </div>
              <span>{opened === p.id ? '−' : '↗'}</span>
            </button>
            {opened === p.id && (
              <div className="post-content">
                <p>{p.body}</p>
                {/* 본인 글일 때만 수정·삭제 버튼 표시 */}
                {p.owner === profile.id && (
                  <div className="button-row">
                    <button
                      className="secondary"
                      onClick={() => setEditing({ ...p })}
                    >
                      수정
                    </button>
                    <button
                      className="text-button danger"
                      onClick={() => setDeleteId(p.id)}
                    >
                      삭제
                    </button>
                  </div>
                )}
                {/* 삭제 확인 */}
                {deleteId === p.id && (
                  <div
                    className="delete-confirm"
                    role="alert"
                  >
                    <span>이 게시글을 삭제할까요?</span>
                    <button
                      className="secondary"
                      onClick={() => {
                        setPosts(
                          posts.filter(
                            (x) => x.id !== p.id || x.owner !== profile.id,
                          ),
                        );
                        setDeleteId(null);
                        setOpened(null);
                        notify('게시글을 삭제했습니다.');
                      }}
                    >
                      삭제 확인
                    </button>
                    <button
                      className="text-button"
                      onClick={() => setDeleteId(null)}
                    >
                      취소
                    </button>
                  </div>
                )}
              </div>
            )}
          </article>
        ))}
        {/* 검색 결과가 없을 때 */}
        {!visible.length && (
          <div className="empty-state">
            <h3>표시할 게시글이 없어요.</h3>
            <p>검색어나 분류를 바꾸거나 첫 글을 작성해 보세요.</p>
          </div>
        )}
      </div>
    </>
  );
}

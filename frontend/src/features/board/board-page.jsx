import { useState } from 'react';
import Icon from '../../components/common/icon.jsx';
import PageHeading from '../../components/common/page-heading.jsx';
import Select from '../../components/common/select.jsx';
import Tag from '../../components/common/tag.jsx';

export default function Board({ posts, setPosts, profile, notify }) {
  const [filter, setFilter] = useState('전체'),
    [query, setQuery] = useState(''),
    [opened, setOpened] = useState(null),
    [editing, setEditing] = useState(null),
    [deleteId, setDeleteId] = useState(null);
  const visible = posts.filter(
    (p) =>
      (filter === '전체' || p.type === filter) &&
      `${p.title} ${p.body} ${p.author}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
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
    setPosts(
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
    notify('게시글을 이 브라우저에 저장했습니다.');
  }
  return (
    <>
      <PageHeading
        eyebrow="COMMUNITY"
        title="무대 밖에서도, 함께."
        description="공연 모집부터 준비 경험까지. 자유롭게 이야기를 나누는 공간입니다."
      />
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

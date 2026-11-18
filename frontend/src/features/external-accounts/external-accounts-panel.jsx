import { useState } from 'react';
import {
  MAX_WORKS,
  accountToReplace,
  displayNameOf,
  toggleWork,
  verificationText,
} from './account-rules.js';
import ExternalLinks from './external-links.jsx';
import { safeLink, safeThumbnail } from './safe-link.js';
import { useExternalAccounts } from './use-external-accounts.js';
import './external-accounts.css';

// 마이페이지 '작업물' 메뉴의 "외부 계정" 칸 (NSU-37·79, API 06)
// 아티스트가 YouTube 채널(OAuth로 제어권 확인)이나 Spotify 아티스트 페이지(주소로 연결)를 연결하고,
// YouTube 영상을 최대 10개 골라 행사 관계자에게 링크로 보여 준다. 재생기는 넣지 않는다.
// 판단 규칙은 account-rules.js, 요청은 external-accounts-api.js, 상태는 use-external-accounts.js에 있다.

export default function ExternalAccountsPanel() {
  const state = useExternalAccounts();
  // 확인 중인 동작: { kind: 'replace' | 'disconnect', provider } | null
  const [confirm, setConfirm] = useState(null);
  const [spotifyUrl, setSpotifyUrl] = useState('');
  // 작업물 고르는 중인 계정: { accountId, rows: 후보, selected: 고른 ID 순서 } | null
  const [picker, setPicker] = useState(null);

  const { usable, items, notice, busy } = state;
  const youtube = accountToReplace(items, 'YOUTUBE');
  const spotify = accountToReplace(items, 'SPOTIFY');

  async function openPicker(account) {
    const result = await state.loadCandidates(account);
    if (!result.ok) return;
    const selected = account.works.map((work) => work.workId);
    // 후보 50개 밖으로 밀려난 예전 선택도 목록에 남겨 순서만 바꿔 저장할 수 있게 한다 (API 06 §8)
    const missing = account.works.filter((work) => !result.value.some((c) => c.workId === work.workId));
    setPicker({ accountId: account.externalAccountId, rows: [...missing, ...result.value], selected });
  }

  async function savePicker(account) {
    const result = await state.chooseWorks(account, picker.selected);
    if (result.ok) setPicker(null);
  }

  async function connectSpotify(event) {
    event.preventDefault();
    const result = await state.connectSpotify(spotifyUrl, spotify);
    if (result.ok) {
      setSpotifyUrl('');
      setConfirm(null);
    }
  }

  if (!usable) {
    return (
      <section className="panel ext-panel">
        <h2>외부 계정</h2>
        <p>YouTube 채널과 Spotify 아티스트 페이지 연결은 서버와 연결된 로그인에서 쓸 수 있어요.</p>
      </section>
    );
  }

  return (
    <section className="panel ext-panel">
      <h2>외부 계정</h2>
      <p>
        YouTube 채널이나 Spotify 아티스트 페이지를 연결하면 행사 관계자가 원래 사이트 링크로 확인해요. 재생은 각
        사이트에서 해요.
      </p>
      <p className="ext-hint">SoundCloud는 약관 때문에 아직 연결할 수 없어요.</p>

      {notice && (
        <p
          className={`ext-notice ${notice.tone === 'error' ? 'error' : 'ok'}`}
          role={notice.tone === 'error' ? 'alert' : 'status'}
        >
          {notice.text}
        </p>
      )}

      {items === null ? (
        <button
          type="button"
          className="secondary"
          onClick={state.reload}
        >
          연결 목록 불러오기
        </button>
      ) : (
        <div className="ext-cards">
          <section className="ext-card">
            <h3>YouTube</h3>
            {youtube ? (
              <>
                <ExternalLinks accounts={[youtube]} />
                {confirm?.provider === 'YOUTUBE' && confirm.kind === 'replace' && (
                  <p className="ext-confirm">
                    기존 연결을 바꿀까요? 고른 영상도 지워져요.{' '}
                    <button
                      type="button"
                      className="primary"
                      disabled={busy}
                      onClick={() => state.connectYoutube(youtube)}
                    >
                      Google에서 다른 채널로 바꾸기
                    </button>{' '}
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => setConfirm(null)}
                    >
                      취소
                    </button>
                  </p>
                )}
                {confirm?.provider === 'YOUTUBE' && confirm.kind === 'disconnect' && (
                  <p className="ext-confirm">
                    연결을 해제할까요? 고른 영상도 지워져요.{' '}
                    <button
                      type="button"
                      className="secondary danger"
                      disabled={busy}
                      onClick={async () => {
                        setPicker(null);
                        setConfirm(null);
                        await state.disconnect(youtube);
                      }}
                    >
                      해제
                    </button>{' '}
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => setConfirm(null)}
                    >
                      취소
                    </button>
                  </p>
                )}
                <div className="ext-actions">
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy}
                    onClick={() => (picker ? setPicker(null) : openPicker(youtube))}
                  >
                    {picker ? '작업물 고르기 닫기' : '작업물 고르기'}
                  </button>
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy}
                    onClick={() => setConfirm({ kind: 'replace', provider: 'YOUTUBE' })}
                  >
                    다른 채널로 바꾸기
                  </button>
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy}
                    onClick={() => setConfirm({ kind: 'disconnect', provider: 'YOUTUBE' })}
                  >
                    해제
                  </button>
                </div>
                {picker?.accountId === youtube.externalAccountId && (
                  <div className="ext-picker">
                    <p>
                      행사 관계자에게 보여 줄 영상을 골라 주세요. 고른 순서대로 보여요.{' '}
                      <strong>
                        {picker.selected.length}/{MAX_WORKS}
                      </strong>
                    </p>
                    {picker.rows.length === 0 ? (
                      <p className="ext-hint">채널에 공개 영상이 없어요.</p>
                    ) : (
                      <ul className="ext-candidates">
                        {picker.rows.map((row) => {
                          const order = picker.selected.indexOf(row.workId) + 1;
                          const thumb = safeThumbnail(row.thumbnailUrl);
                          return (
                            <li key={row.workId}>
                              <button
                                type="button"
                                aria-pressed={order > 0}
                                onClick={() =>
                                  setPicker({ ...picker, selected: toggleWork(picker.selected, row.workId) })
                                }
                              >
                                <span
                                  className="ext-order"
                                  aria-hidden="true"
                                >
                                  {order > 0 ? order : ''}
                                </span>
                                {thumb && (
                                  <img
                                    className="ext-thumb"
                                    src={thumb}
                                    alt=""
                                    loading="lazy"
                                    referrerPolicy="no-referrer"
                                  />
                                )}
                                <span>{row.title ?? 'YouTube 영상'}</span>
                                {order > 0 && <span className="ext-sr-only"> ({order}번째로 고름)</span>}
                              </button>
                              {safeLink('YOUTUBE', row.url) && (
                                <a
                                  href={row.url}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                >
                                  YouTube에서 보기
                                </a>
                              )}
                            </li>
                          );
                        })}
                      </ul>
                    )}
                    <button
                      type="button"
                      className="primary"
                      disabled={busy}
                      onClick={() => savePicker(youtube)}
                    >
                      저장
                    </button>
                  </div>
                )}
              </>
            ) : (
              <>
                <p className="ext-hint">
                  Google 계정으로 로그인해 채널의 주인임을 확인해요. 읽기 권한만 쓰고 토큰은 저장하지 않아요.
                </p>
                <button
                  type="button"
                  className="primary"
                  disabled={busy}
                  onClick={() => state.connectYoutube(null)}
                >
                  YouTube 채널 연결
                </button>
              </>
            )}
          </section>

          <section className="ext-card">
            <h3>Spotify</h3>
            {spotify && (
              <>
                <ExternalLinks accounts={[spotify]} />
                <div className="ext-actions">
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy}
                    onClick={() => setConfirm({ kind: 'disconnect', provider: 'SPOTIFY' })}
                  >
                    해제
                  </button>
                </div>
                {confirm?.provider === 'SPOTIFY' && confirm.kind === 'disconnect' && (
                  <p className="ext-confirm">
                    연결을 해제할까요?{' '}
                    <button
                      type="button"
                      className="secondary danger"
                      disabled={busy}
                      onClick={async () => {
                        setConfirm(null);
                        await state.disconnect(spotify);
                      }}
                    >
                      해제
                    </button>{' '}
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => setConfirm(null)}
                    >
                      취소
                    </button>
                  </p>
                )}
              </>
            )}
            <form
              className="stacked-form"
              onSubmit={connectSpotify}
              noValidate
            >
              <label>
                {spotify ? '다른 아티스트 주소로 바꾸기' : 'Spotify 아티스트 주소'}
                <input
                  type="url"
                  value={spotifyUrl}
                  onChange={(e) => setSpotifyUrl(e.target.value)}
                  placeholder="https://open.spotify.com/artist/…"
                />
              </label>
              {spotify && spotifyUrl.trim() && (
                <p className="ext-hint">
                  기존 연결(<strong>{displayNameOf(spotify)}</strong>, {verificationText(spotify)})을 바꿀까요?
                </p>
              )}
              <button
                className="secondary"
                type="submit"
                disabled={busy || !spotifyUrl.trim()}
              >
                {spotify ? '바꾸기' : 'Spotify 연결'}
              </button>
              <p className="ext-hint">
                주소로 연결하므로 Spotify 계정의 주인임을 확인하지는 않아요. 확인 배지도 켜지지 않아요.
              </p>
            </form>
          </section>
        </div>
      )}
    </section>
  );
}

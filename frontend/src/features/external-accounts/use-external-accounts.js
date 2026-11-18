import { useCallback, useEffect, useState } from 'react';
import { AUTH_API_MODE } from '../auth/api/auth-api.js';
import { tokenStore } from '../auth/api/auth-token.js';
import { followUpFor } from './account-rules.js';
import {
  ERROR_MESSAGES,
  ExternalAccountsApiError,
  createExternalAccountsApi,
} from './external-accounts-api.js';
import { safeAuthorizationUrl } from './safe-link.js';
import { createStartupConnection } from './startup-connection.js';
import { startupLink } from './startup-link.js';

// 외부 계정 화면의 상태와 동작 (API 06). 판단 규칙은 account-rules.js, 요청은 external-accounts-api.js에 있습니다.

const api = createExternalAccountsApi({ authHeader: () => tokenStore.authHeader() });
// 콜백으로 돌아온 값으로 연결 요청을 보내는 일은 앱이 사는 동안 한 번뿐이다 (StrictMode 이중 실행 포함)
const runStartupConnection = createStartupConnection(startupLink, api);

const messageOf = (error) =>
  error instanceof ExternalAccountsApiError ? error.message : ERROR_MESSAGES.UNKNOWN_ERROR;

// 서버 연결 모드에서 로그인한 아티스트일 때만 쓸 수 있다. 체험(demo) 모드에는 이 API가 없다
export function useExternalAccounts() {
  const artistId = tokenStore.read()?.userId;
  const usable = AUTH_API_MODE === 'server' && Boolean(artistId);

  // items: null이면 아직 못 읽음. notice: { tone: 'ok' | 'error', text }
  const [items, setItems] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);

  const reload = useCallback(async () => {
    try {
      setItems(await api.list(artistId));
    } catch (error) {
      setNotice({ tone: 'error', text: messageOf(error) });
    }
  }, [artistId]);

  // 화면이 처음 뜰 때: 콜백으로 돌아온 값이 있으면 연결하고, 그다음 목록을 읽는다
  useEffect(() => {
    if (!usable) return undefined;
    let cancelled = false;
    (async () => {
      const pending = runStartupConnection();
      if (pending) {
        const result = await pending;
        if (cancelled) return;
        setNotice(
          result.connected
            ? { tone: 'ok', text: 'YouTube 채널을 연결했어요. 채널 제어권을 확인했어요.' }
            : { tone: 'error', text: result.message },
        );
      }
      await reload();
    })();
    return () => {
      cancelled = true;
    };
  }, [usable, reload]);

  // 동작 하나를 실행한다. 실패하면 안내를 띄우고, 필요하면 목록을 다시 읽는다. { ok, value }를 돌려준다
  const perform = useCallback(
    async (action, { okText } = {}) => {
      setBusy(true);
      setNotice(null);
      try {
        const value = await action();
        if (okText) setNotice({ tone: 'ok', text: okText });
        return { ok: true, value };
      } catch (error) {
        setNotice({ tone: 'error', text: messageOf(error) });
        if (followUpFor(error.code) === 'reload') await reload();
        return { ok: false };
      } finally {
        setBusy(false);
      }
    },
    [reload],
  );

  return {
    usable,
    items,
    notice,
    busy,
    reload,
    // 같은 탭에서 Google 동의 화면으로 이동한다. 돌아오면 앱이 처음 뜰 때 위 효과가 이어받는다
    connectYoutube: (replaceAccount) =>
      perform(async () => {
        const { authorizationUrl } = await api.startAuthorization({
          replaceExternalAccountId: replaceAccount?.externalAccountId ?? null,
        });
        const url = safeAuthorizationUrl(authorizationUrl);
        if (!url) throw new ExternalAccountsApiError('EXTERNAL_ACCOUNT_AUTHORIZATION_FAILED');
        window.location.assign(url);
      }),
    connectSpotify: (url, replaceAccount) =>
      perform(
        async () => {
          await api.connectSpotify({
            url: url.trim(),
            replaceExternalAccountId: replaceAccount?.externalAccountId ?? null,
          });
          await reload();
        },
        { okText: 'Spotify 아티스트 프로필을 연결했어요.' },
      ),
    disconnect: (account) =>
      perform(
        async () => {
          await api.disconnect(account.externalAccountId);
          await reload();
        },
        { okText: '연결을 해제했어요.' },
      ),
    loadCandidates: (account) => perform(() => api.workCandidates(account.externalAccountId)),
    chooseWorks: (account, workIds) =>
      perform(
        async () => {
          const updated = await api.chooseWorks(account.externalAccountId, workIds);
          setItems((current) =>
            current?.map((item) => (item.externalAccountId === updated.externalAccountId ? updated : item)),
          );
        },
        { okText: workIds.length === 0 ? '고른 작업물을 모두 내렸어요.' : `작업물 ${workIds.length}개를 저장했어요.` },
      ),
  };
}

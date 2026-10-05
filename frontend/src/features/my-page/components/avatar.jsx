import Icon from '../../../components/common/icon.jsx';
import { AVATAR_COLORS } from '../avatar-colors.js';

// 프로필 사진 표시: 사진 → 이니셜 → 기본 사람 아이콘 순서로 보여 줍니다.
// avatar 값 예: { type: 'photo', src: 'data:image/webp;base64,…' } / { type: 'initial', color: 'blue' }

export default function Avatar({ profile, size = 56 }) {
  const avatar = profile.avatar;
  const style = { width: size, height: size };
  // 브라우저에서 만든 이미지(data:image/…)만 표시 (저장값이 바뀌어도 외부 주소를 열지 않음)
  if (avatar?.type === 'photo' && /^data:image\/(webp|png|jpeg);base64,/.test(avatar.src || '')) {
    return (
      <img
        className="avatar"
        style={style}
        src={avatar.src}
        alt=""
      />
    );
  }
  if (avatar?.type === 'initial') {
    const color = AVATAR_COLORS.some((c) => c.value === avatar.color) ? avatar.color : 'blue';
    return (
      <span
        className={`avatar avatar-initial ${color}`}
        style={{ ...style, fontSize: size * 0.42 }}
        aria-hidden="true"
      >
        {profile.name.trim().charAt(0) || '?'}
      </span>
    );
  }
  return (
    <span
      className="avatar avatar-default"
      style={style}
      aria-hidden="true"
    >
      <Icon
        name="user"
        size={Math.round(size * 0.54)}
      />
    </span>
  );
}

import Icon from '../../../components/common/icon.jsx';
import ReferenceUpload from './reference-upload.jsx';
import EventFields from './event-fields.jsx';

// 참고 곡으로 찾기 입력 (NSU-111): 참고 곡 올리기 + 행사 조건 → '이 곡과 비슷한 아티스트 찾기'
// 찾기 버튼은 올릴 수 있는 파일을 고른 뒤에만 켜집니다.
export default function SongSearchForm({ modeSwitch, form, change, reference, setReference, error, onSubmit }) {
  const ready = reference?.status === 'ready';
  return (
    <form
      className="panel match-form song-form"
      onSubmit={onSubmit}
    >
      {modeSwitch}
      <h2 className="song-form-title">참고 곡</h2>
      <ReferenceUpload
        value={reference}
        onChange={setReference}
      />

      <div className="form-divider" />
      <h2 className="song-form-title">행사 조건</h2>
      <p className="field-note">일정·지역·예산으로 섭외할 수 있는 아티스트만 남겨요.</p>
      <EventFields
        form={form}
        change={change}
      />

      {error && (
        <p
          role="alert"
          className="error"
        >
          {error}
        </p>
      )}
      <button
        className="primary full"
        type="submit"
        disabled={!ready}
      >
        <Icon
          name="search"
          size={18}
        />{' '}
        이 곡과 비슷한 아티스트 찾기
      </button>
      <p className="field-note">
        {ready
          ? '같은 행사는 1분에 한 번, 하루 5번까지 추천을 받을 수 있어요.'
          : '참고 곡을 올리면 찾기 버튼이 켜져요.'}
      </p>
    </form>
  );
}

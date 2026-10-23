import Icon from '../../../components/common/icon.jsx';

// 마이페이지 '섭외 요청' 메뉴 (아티스트): 행사 관계자가 보낸 섭외 요청 우편함
// 섭외 요청 API가 연결되기 전이라 빈 상태만 보여 줍니다. (NSU-110에서 '작업물·섭외' 탭을 나눔)
export default function BookingRequestsTab() {
  return (
    <section className="panel booking-requests">
      <h2>받은 섭외 요청</h2>
      <p>새로운 섭외 요청이 도착하면 행사명, 일정, 지역과 요청 상태를 이곳에서 확인해요.</p>
      <div className="booking-empty">
        <Icon
          name="inbox"
          size={28}
        />
        <strong>아직 도착한 섭외 요청이 없어요</strong>
        <span>섭외 요청 기능이 연결되면 받은 요청이 여기에 표시돼요.</span>
      </div>
    </section>
  );
}

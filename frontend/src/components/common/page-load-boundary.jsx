import { Component } from 'react';
import './page-loading.css';

// 화면 코드를 받아 오지 못했을 때(네트워크 끊김, 새로 배포되어 예전 파일이 사라짐 등) 안내 (NSU-182)
// React.lazy는 실패한 불러오기를 기억하므로, [다시 시도]는 페이지를 새로 불러옵니다.
// 화면 안의 다른 오류도 여기서 멈춰서 앱 전체가 하얗게 비지 않게 합니다.
// 다른 메뉴로 가면 App이 key를 바꿔 이 상자를 새로 만듭니다.
export default class PageLoadBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { failed: false };
  }

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div
        className="page-load-error"
        role="alert"
      >
        <strong>화면을 불러오지 못했어요.</strong>
        <p>인터넷 연결을 확인한 뒤 다시 시도해 주세요.</p>
        <button
          type="button"
          className="secondary"
          onClick={() => window.location.reload()}
        >
          다시 시도
        </button>
      </div>
    );
  }
}

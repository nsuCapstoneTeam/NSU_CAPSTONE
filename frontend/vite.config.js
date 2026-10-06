import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 개발 서버(npm run dev)에서 /api 로 시작하는 요청을 백엔드(Spring Boot, 8080)로 넘김
    // → 브라우저는 같은 주소(5173)로 요청하므로 CORS 설정 없이 백엔드를 부를 수 있음
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})

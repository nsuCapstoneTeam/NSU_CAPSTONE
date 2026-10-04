import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './app.jsx';

// index.html의 #root에 App을 그립니다. StrictMode는 개발 중 잠재적 문제를 알려 줍니다.
createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);

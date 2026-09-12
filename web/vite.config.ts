import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

const backendTarget = process.env.VITE_BACKEND_TARGET ?? 'http://127.0.0.1:8080';
const buildCommit = process.env.VITE_BUILD_COMMIT ?? 'unknown';

if (!/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(backendTarget)) {
  throw new Error('VITE_BACKEND_TARGET must be an HTTP loopback URL');
}
if (!/^(unknown|[0-9a-f]{7,40})$/.test(buildCommit)) {
  throw new Error('VITE_BUILD_COMMIT must be unknown or a 7-40 character lowercase Git commit');
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    {
      name: 'release-build-identity',
      transformIndexHtml(html) {
        return html.replace('__YCSOPEN_BUILD_COMMIT__', buildCommit);
      },
    },
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '@lib': path.resolve(__dirname, 'lib'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: backendTarget,
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./test/setup.ts'],
    include: ['test/unit/**/*.test.{ts,tsx}'],
  },
});

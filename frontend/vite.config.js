import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "../src/main/resources/static",
    emptyOutDir: true
  },
  server: {
    port: 5173,
    host: "127.0.0.1",
    strictPort: false,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8086",
        changeOrigin: true
      },
      "/ws": {
        target: "ws://127.0.0.1:8086",
        ws: true,
        changeOrigin: true
      }
    }
  }
});

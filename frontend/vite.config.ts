import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "path";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { "@": path.resolve(__dirname, "./src") },
  },
  server: {
    proxy: {
      // Order matters: more specific patterns must come before general ones.
      "^/api/v1/auth": {
        target: "http://localhost:8081", // auth-service
        changeOrigin: true,
      },
      "^/api/v1/taxpayers/[^/]+/filings": {
        target: "http://localhost:8080", // refund-service
        changeOrigin: true,
      },
      "^/api/v1/taxpayers": {
        target: "http://localhost:8082", // taxpayer-service
        changeOrigin: true,
      },
      "^/api/v1/(predictions|guidance)": {
        target: "http://localhost:8083", // ai-service
        changeOrigin: true,
      },
    },
  },
});

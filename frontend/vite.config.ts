import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

const WRITE_PATH_PATTERN = /^\/api\/(transfer|accounts\/[^/]+\/(earn|spend|reversal))$/;

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "");
  const upstreamApiKey = env.UPSTREAM_API_KEY ?? "dev-api-key";

  return {
    plugins: [react()],
    server: {
      host: "0.0.0.0",
      port: 5173,
      proxy: {
        "/api": {
          target: "http://localhost:8080",
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ""),
          configure: (proxy) => {
            proxy.on("proxyReq", (proxyReq, req) => {
              const requestPath = req.url ?? "";
              const isWrite = req.method === "POST" && WRITE_PATH_PATTERN.test(requestPath);
              if (isWrite) {
                proxyReq.setHeader("X-API-Key", upstreamApiKey);
              } else {
                proxyReq.removeHeader("X-API-Key");
              }
            });
          }
        }
      }
    },
    test: {
      environment: "jsdom",
      setupFiles: "./src/test/setup.ts"
    }
  };
});

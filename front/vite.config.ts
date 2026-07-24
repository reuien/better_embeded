import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  build: {
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) {
            return undefined;
          }
          if (id.includes("echarts")) {
            return "echarts";
          }
          if (id.includes("element-plus") || id.includes("@element-plus")) {
            return "element-plus";
          }
          if (id.includes("@vueuse")) {
            return "vueuse";
          }
          if (id.includes("vue")) {
            return "vue";
          }
          return "vendor";
        },
      },
    },
  },
  server: {
    proxy: {
      "/api": "http://127.0.0.1:8080",
    },
  },
});

import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Served from a sub-path on GitHub Pages ("/kandkmedia/") by default. When the
// site is hosted at the root of its own domain (e.g. portal.kandkmedia.co.za on
// Render), build with VITE_BASE=/ .
export default defineConfig({
  plugins: [react()],
  base: process.env.VITE_BASE || "/kandkmedia/",
});

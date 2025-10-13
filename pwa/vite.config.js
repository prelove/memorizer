import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig(({ command }) => ({
  // Serve under subpath when built and hosted by desktop at /pwa/
  base: command === 'build' ? '/pwa/' : '/',
  server: {
    host: true // expose on LAN for dev when running `npm run dev`
  },
  preview: {
    host: true
  },
  plugins: [
    vue(),
    VitePWA({
      registerType: 'prompt',
      manifest: {
        name: 'Memorizer PWA',
        short_name: 'Memorizer',
        description: 'Mobile companion for Memorizer',
        theme_color: '#1f2327',
        background_color: '#1f2327',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png' }
        ]
      }
    })
  ]
}))

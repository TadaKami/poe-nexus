/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Палитра в духе Path of Exile
        poe: {
          bg: '#0d0a08',
          panel: '#1a140f',
          gold: '#c8a95b',
          blood: '#8a1f1f'
        }
      }
    }
  },
  plugins: []
}
/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Plus Jakarta Sans"', 'system-ui', '-apple-system', 'sans-serif'],
      },
      colors: {
        jf: {
          lavender: '#C4C3E3',
          violet: '#434164',
          'violet-hover': '#32304C',
          cream: '#FCF9EE',
          'cream-darker': '#F4EEDC',
          canvas: '#F6F5FB',
          sage: '#8A9E48',
          apricot: '#FCDD9D',
          orange: '#E65100',
          ink: '#201F35',
          'ink-muted': '#5C5A78',
          'text-orange': '#C43D00',
          'border-subtle': '#2B2A45',
          'border-strong': '#161524',
        },
        // Existing legacy colors preserved for rest of app
        court: {
          50: '#f8fafc',
          100: '#f1f5f9',
          200: '#e2e8f0',
          700: '#334155',
          800: '#1e293b',
          900: '#0f172a',
          navy: '#1e3a8a',
          gold: '#b45309',
        }
      },
      borderRadius: {
        'card': 'var(--jf-radius-card)',
        'input': 'var(--jf-radius-input)',
      },
      boxShadow: {
        'frame': '0 25px 70px -15px rgba(43, 42, 69, 0.2), 0 0 1px 1px rgba(80, 78, 118, 0.08)',
        'card': '0 4px 20px -2px rgba(43, 42, 69, 0.07), 0 2px 6px -1px rgba(80, 78, 118, 0.04), 0 0 0 1px rgba(80, 78, 118, 0.07)',
        'card-soft': '0 10px 25px -5px rgba(43, 42, 69, 0.08), 0 0 1px rgba(80, 78, 118, 0.05)',
        'card-hover': '0 16px 36px -6px rgba(43, 42, 69, 0.14), 0 4px 12px -2px rgba(80, 78, 118, 0.08), 0 0 0 1px rgba(80, 78, 118, 0.15)',
        'glow-violet': '0 0 25px -4px rgba(80, 78, 118, 0.3)',
        'glow-orange': '0 0 25px -4px rgba(241, 100, 46, 0.35)',
        'glow-apricot': '0 0 25px -4px rgba(252, 221, 157, 0.5)',
        'glow-sage': '0 0 25px -4px rgba(163, 181, 101, 0.3)',
        'glow-btn': '0 8px 25px -4px rgba(80, 78, 118, 0.4), 0 0 15px rgba(241, 100, 46, 0.18)',
      },
      transitionTimingFunction: {
        'entrance': 'cubic-bezier(0.16, 1, 0.3, 1)',
      }
    },
  },
  plugins: [],
}

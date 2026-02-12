/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx,css}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        primary: '#3211d4',
        secondary: '#d411b5',
        'background-light': '#f6f6f8',
        'background-dark': '#131022',
        'surface-dark': '#1c1833',
        'surface-darker': '#0e0c19',
      },
      fontFamily: {
        display: ['Spline Sans', 'sans-serif'],
      },
      borderRadius: {
        DEFAULT: '0.25rem',
        lg: '0.5rem',
        xl: '0.75rem',
        '2xl': '1rem',
        full: '9999px',
      },
      boxShadow: {
        neon: '0 0 15px rgba(50, 17, 212, 0.4)',
        'neon-pink': '0 0 15px rgba(212, 17, 181, 0.4)',
      },
    },
  },
  plugins: [],
};

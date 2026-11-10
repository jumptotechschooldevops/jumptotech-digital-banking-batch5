/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        navy: {
          50: "#f0f4f9",
          100: "#d9e4f0",
          200: "#b3c9e0",
          300: "#8caecf",
          400: "#5a86b3",
          500: "#2f5f94",
          600: "#1f477a",
          700: "#163761",
          800: "#102848",
          900: "#0a1b33",
          950: "#050e1c",
        },
        teal: {
          50: "#eefcfa",
          100: "#d2f6f1",
          200: "#a6ece3",
          300: "#70dcce",
          400: "#3cc4b3",
          500: "#1fa696",
          600: "#17847a",
          700: "#166a63",
          800: "#145550",
          900: "#124643",
        },
      },
      fontFamily: {
        sans: [
          "Inter",
          "ui-sans-serif",
          "system-ui",
          "-apple-system",
          "Segoe UI",
          "Roboto",
          "Helvetica Neue",
          "Arial",
          "sans-serif",
        ],
      },
    },
  },
  plugins: [],
};

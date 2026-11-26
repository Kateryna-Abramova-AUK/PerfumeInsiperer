import js from '@eslint/js'
import globals from 'globals'

export default [
  { ignores: ['dist/**'] },
  {
    files: ['src/**/*.{js,jsx}'],
    ...js.configs.recommended,
    languageOptions: {
      globals: globals.browser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
  },
  {
    files: ['*.js'],
    ...js.configs.recommended,
    languageOptions: { globals: globals.node },
  },
]

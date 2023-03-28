module.exports = {
  env: {
    browser: true,
    es2021: true
  },
  extends: [
    'plugin:react/recommended',
    'standard-with-typescript',
    "plugin:import/typescript",
    "plugin:prettier/recommended",
    "plugin:react-hooks/recommended"
  ],
  overrides: [],
  parser: "@typescript-eslint/parser",
  parserOptions: {
    ecmaFeatures: {
      jsx: false,
    },
    ecmaVersion: "latest",
    sourceType: "module",
    project: "./tsconfig.json",
    tsconfigRootDir: __dirname,
  },
  plugins: [
    'react',
    "jsx-a11y"
  ],
  rules: {
    "arrow-parens": "off",
    'react/no-array-index-key': "error",
    "react/react-in-jsx-scope": "off",
    "react/jsx-uses-react": ["off"],
    'react/jsx-props-no-spreading': 'off',
    "react/no-unescaped-entities": ["off"],
    "react/jsx-one-expression-per-line": "off",
    "react-hooks/exhaustive-deps": "off",
    "@typescript-eslint/await-thenable": "off",
    "@typescript-eslint/restrict-template-expressions": "off",
    "@typescript-eslint/no-explicit-any": "off",
    "@typescript-eslint/explicit-function-return-type": "off",
    "@typescript-eslint/no-non-null-assertion": "off",
    "@typescript-eslint/no-unnecessary-type-assertion": "off",
    "@typescript-eslint/prefer-nullish-coalescing": "off",
    "@typescript-eslint/quotes": "off",
    "@typescript-eslint/strict-boolean-expressions": "off",
    "@typescript-eslint/no-confusing-void-expression": "off",
    "@typescript-eslint/no-floating-promises": "off",
    "@typescript-eslint/triple-slash-reference": "off",
    "@typescript-eslint/no-misused-promises": [2, {
      "checksVoidReturn": {
        "attributes": false
      }
    }],
    "no-console": "off",
    "import/order": [
      "error",
      {
        groups: ["builtin", "external", "internal"],
        pathGroups: [{
          pattern: "react",
          group: "external",
          position: "before",
        }, ],
        pathGroupsExcludedImportTypes: ["react"],
        "newlines-between": "always",
        alphabetize: {
          order: "asc",
          caseInsensitive: true,
        },
      },
    ],
  },
  settings: {
    react: {
      version: "detect",
    },
  },
  ignorePatterns: [
    ".eslintrc.js",
    "dist",
    "node_modules",
    "prettier.config.js",
    "public",
    "scripts/",
    "vite.config.ts"
  ],
}
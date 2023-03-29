# Open Digital Education Explorer

This is a [ReactJS](https://reactjs.org) + [Vite](https://vitejs.dev) app.

## What is inside?

Many tools are already configured like:

- [ReactJS](https://reactjs.org)
- [Vite](https://vitejs.dev)
- [TypeScript](https://www.typescriptlang.org)
- [...](./TOOLS.md)

[See all tools](./TOOLS.md)

## Getting Started

### Install

Install all dependencies.

```bash
pnpm install
```

## Dev

### Start project

Open your project with Vite Server + HMR at <http://localhost:3000>.

```bash
pnpm dev
```

### [Server Options](https://vitejs.dev/config/server-options.html)

You can change Vite Server by editing `vite.config.ts`

```bash
server: {
  host: "0.0.0.0",
  port: 3000,
  open: true // open the page on <http://localhost:3000> when dev server starts.
}
```

### Absolute Imports

You should use absolute imports in your app

```bash
Replace ../components/* by components/*
```

Edit `vite.config.ts` and add an `alias`

> Telling Vite how to build import path:

```bash
alias: [
  { find: "~", replacement: path.resolve(__dirname, "src") },
  {
    find: "components",
    replacement: path.resolve(__dirname, "./src/components"),
  },
]
```

Add your new path to `tsconfig.json`:

> Telling TypeScript how to resolve import path:

```bash
"baseUrl": "./src",
"paths": {
  "components/*": ["./components/*"],
}
```

### Lint

```bash
pnpm lint
```

### Prettier

Prettier everything once

```bash
pnpm format
```

### Lighthouse

> LHCI will check if your app respect at least 90% of these categories: performance, a11y, Best practices and seo

```bash
pnpm lighthouse
```

### Pre-commit

When committing your work, `pre-commit` will start `yarn lint-staged`:

> lint-staged starts lint + prettier

```bash
pnpm pre-commit
```

## Build

TypeScript check + Vite Build

```bash
pnpm build
```

## Preview

```bash
pnpm preview
```

## License

This project is licensed under the AGPL-3.0 license.

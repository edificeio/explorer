import path from "path";

export const resolve = {
  alias: [
    { find: "~", replacement: path.resolve(__dirname, "src") },
    {
      find: "@app",
      replacement: path.resolve(__dirname, "../src/app"),
    },
    {
      find: "@assets",
      replacement: path.resolve(__dirname, "../src/assets"),
    },
    {
      find: "@config",
      replacement: path.resolve(__dirname, "../src/config"),
    },
    {
      find: "@components",
      replacement: path.resolve(__dirname, "../src/components"),
    },
    {
      find: "@contexts",
      replacement: path.resolve(__dirname, "../src/contexts"),
    },
    {
      find: "@features",
      replacement: path.resolve(__dirname, "../src/features"),
    },
    {
      find: "@hooks",
      replacement: path.resolve(__dirname, "../src/hooks"),
    },
    {
      find: "@pages",
      replacement: path.resolve(__dirname, "../src/pages"),
    },
    {
      find: "@queries",
      replacement: path.resolve(__dirname, "../src/queries"),
    },
    {
      find: "@services",
      replacement: path.resolve(__dirname, "../src/services"),
    },
    {
      find: "@shared",
      replacement: path.resolve(__dirname, "../src/shared"),
    },
    {
      find: "@store",
      replacement: path.resolve(__dirname, "../src/store"),
    },
  ],
};

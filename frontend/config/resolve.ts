import path from "path";

export const resolve = {
  alias: [
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
      find: "@shared",
      replacement: path.resolve(__dirname, "../src/shared"),
    },
    {
      find: "@services",
      replacement: path.resolve(__dirname, "../src/services"),
    },
    {
      find: "@store",
      replacement: path.resolve(__dirname, "../src/store"),
    },
  ],
};

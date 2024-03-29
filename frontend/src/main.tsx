import React, { StrictMode } from "react";

import "./i18n";

import { OdeClientProvider, ThemeProvider } from "@edifice-ui/react";
import {
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { createRoot } from "react-dom/client";

import Root from "./app/root";
import { getExplorerConfig } from "./config/getExplorerConfig";

const root = document.getElementById("root");

if (process.env.NODE_ENV !== "production") {
  import("@axe-core/react").then((axe) => {
    axe.default(React, root, 1000);
  });
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => {
      if (typeof error === "string") {
        if (error === "0090") window.location.replace("/auth/login");
      }
    },
  }),
  defaultOptions: {
    queries: {
      retry: false,
      refetchOnWindowFocus: false,
    },
  },
});

const getHTMLConfig = getExplorerConfig();

createRoot(root!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <OdeClientProvider
        params={{
          app: getHTMLConfig.app,
        }}
      >
        <ThemeProvider>
          <Root />
        </ThemeProvider>
      </OdeClientProvider>
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
    ,
  </StrictMode>,
);

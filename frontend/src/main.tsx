import React, { StrictMode } from "react";

import { OdeClientProvider } from "@ode-react-ui/core";
import {
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { createRoot } from "react-dom/client";

import Root from "~app/Root";
import { getAppParams } from "~shared/utils/getAppParams";

const root = document.getElementById("root");

if (process.env.NODE_ENV !== "production") {
  import("@axe-core/react").then((axe) => {
    axe.default(React, root, 1000);
  });
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => {
      if (error === "0090") window.location.replace("/auth/login");
    },
  }),
  defaultOptions: {
    queries: {
      retry: false,
      refetchOnWindowFocus: false,
    },
  },
});

createRoot(root!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <OdeClientProvider params={getAppParams()}>
        <Root />
      </OdeClientProvider>
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </StrictMode>,
);

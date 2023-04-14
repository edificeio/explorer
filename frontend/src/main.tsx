import { StrictMode } from "react";

import { OdeClientProvider } from "@ode-react-ui/core";
import {
  configurationFramework,
  http,
  notifyFramework,
  sessionFramework,
} from "@shared/constants";
import { getAppParams } from "@shared/utils/getAppParams";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { createRoot } from "react-dom/client";

import App from "./app/App";

const root = document.getElementById("root");

/* if (process.env.NODE_ENV !== "production") {
  import("@axe-core/react").then((axe) => {
    axe.default(React, root, 1000);
  });
} */

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false, // default: true
    },
  },
});

createRoot(root!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <OdeClientProvider
        framework={{
          sessionFramework,
          configurationFramework,
          notifyFramework,
          http,
        }}
        params={getAppParams()}
      >
        <App />
        <ReactQueryDevtools initialIsOpen={false} />
      </OdeClientProvider>
    </QueryClientProvider>
  </StrictMode>,
);

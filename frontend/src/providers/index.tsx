import { OdeClientProvider, ThemeProvider } from "@edifice-ui/react";
import {
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { ERROR_CODE } from "edifice-ts-client";
import { ReactNode } from "react";
import { getExplorerConfig } from "~/config/getExplorerConfig";

export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => {
      if (typeof error === "string") {
        if (error === ERROR_CODE.NOT_LOGGED_IN)
          window.location.replace("/auth/login");
      }
    },
  }),
  defaultOptions: {
    queries: {
      retry: false,
      refetchOnWindowFocus: false,
      // staleTime: 1000 * 60 * 2,
    },
  },
});

const getHTMLConfig = getExplorerConfig();

export const Providers = ({ children }: { children: ReactNode }) => {
  return (
    <QueryClientProvider client={queryClient}>
      <OdeClientProvider
        params={{
          app: getHTMLConfig.app,
        }}
      >
        <ThemeProvider>{children}</ThemeProvider>
      </OdeClientProvider>
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  );
};

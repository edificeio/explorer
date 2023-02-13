import { Header } from "@ode-react-ui/advanced";
import { Button, Main, useOdeClient } from "@ode-react-ui/core";
import { clsx } from "@shared/config/index";
import { configurationFramework } from "@shared/constants";
import { ErrorBoundary } from "react-error-boundary";
import { Toaster } from "react-hot-toast";

import Explorer from "./Explorer";

function App() {
  const { session, theme } = useOdeClient();

  const is1d: boolean = theme?.is1D;
  const basePath: string = theme?.basePath;

  const ErrorFallback = () => {
    return (
      <div
        className="text-red-500 w-screen h-screen flex flex-col justify-center items-center"
        role="alert"
      >
        <h2 className="text-lg font-semibold">
          Ooops, something went wrong :({" "}
        </h2>
        <Button
          className="mt-4"
          onClick={() => window.location.assign(window.location.origin)}
        >
          Refresh
        </Button>
      </div>
    );
  };

  if (!session || session.notLoggedIn) {
    return (
      <div className="d-grid min-vh-100 align-items-center justify-content-center">
        <a href="/auth/login" target="_blank" rel="noreferrer">
          S'identifier sur le backend...
        </a>
      </div>
    );
  }

  return (
    <>
      <Header
        is1d={is1d}
        src={basePath}
        configurationFramework={configurationFramework}
      />
      <ErrorBoundary FallbackComponent={ErrorFallback}>
        <Main
          className={clsx("container-fluid bg-white", {
            "rounded-4 border": is1d,
            "mt-24": is1d,
          })}
        >
          <Explorer />
        </Main>
      </ErrorBoundary>
      <Toaster
        toastOptions={{
          position: "top-right",
        }}
      />
    </>
  );
}

export default App;

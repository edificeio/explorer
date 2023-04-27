import Explorer from "@app/Explorer";
import { Header } from "@ode-react-ui/advanced";
import { Main, useOdeClient } from "@ode-react-ui/core";
import { clsx } from "@shared/config/index";
import { configurationFramework } from "@shared/constants";
import { Toaster } from "react-hot-toast";

function App() {
  const { session, is1d, basePath } = useOdeClient();

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
      <Main
        className={clsx("container-fluid bg-white", {
          "rounded-4 border": is1d,
          "mt-24": is1d,
        })}
      >
        <Explorer />
      </Main>
      <Toaster
        toastOptions={{
          position: "top-right",
        }}
      />
    </>
  );
}

export default App;

import { ExplorerProvider } from "@contexts/ExplorerContext/ExplorerContext";
import { useOdeBackend } from "@hooks/useOdeBackend";
import { Header, Main } from "@ode-react-ui/core";
import { clsx } from "@shared/config/index";
import { OdeProviderParams } from "@shared/types";
import { RESOURCE } from "ode-ts-client";
import { Link } from "react-router-dom";

import Explorer from "./Explorer";

function App({ params }: { params: OdeProviderParams }) {
  const {
    session,
    theme,
    explorerFramework,
    i18n,
    app,
    http,
    currentLanguage,
  } = useOdeBackend(params);

  const is1d = theme?.is1D;
  const basePath = theme?.basePath as string;

  if (!session || session.notLoggedIn) {
    return (
      <div className="d-grid min-vh-100 align-items-center justify-content-center">
        <Link to="/auth/login" target="_blank" rel="noreferrer">
          S'identifier sur le backend...
        </Link>
      </div>
    );
  }

  console.count("App");

  return (
    <div className="App">
      <Header is1d={is1d} src={`${basePath}/img/illustrations/logo.png`} />
      <Main
        className={clsx("container-fluid bg-white", {
          "rounded-4 border": is1d,
          "mt-24": is1d,
        })}
      >
        <ExplorerProvider
          explorerFramework={explorerFramework}
          params={params}
          types={[RESOURCE.BLOG]}
          i18n={i18n}
          session={session}
          http={http}
          app={app}
        >
          <Explorer currentLanguage={currentLanguage} />
        </ExplorerProvider>
      </Main>
    </div>
  );
}

export default App;

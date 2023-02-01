import { ExplorerProvider } from "@contexts/useExplorerContext";
import HotToast from "@features/HotToast/HotToast";
import useHotToast from "@features/HotToast/useHotToast";
import { useOdeBackend } from "@hooks/useOdeBackend";
import { Button, Header, Main } from "@ode-react-ui/core";
import { clsx } from "@shared/config/index";
import { OdeProviderParams } from "@shared/types";
import { RESOURCE } from "ode-ts-client";
import { Link } from "react-router-dom";

import Explorer from "./Explorer";

function App({ params }: { params: OdeProviderParams }) {
  const { session, theme, i18n, app, http, currentLanguage } =
    useOdeBackend(params);

  const is1d: boolean = theme?.is1D;
  const basePath: string = theme?.basePath;
  const { hotToast } = useHotToast();

  const infoNotify = () => hotToast.info(<h2>Info: Exemple avec un H2</h2>);
  const warningNotify = () =>
    hotToast.warning("Warning: Exemple avec du texte brut!");
  const sucessNotify = () =>
    hotToast.success("Sucess: Exemple avec du texte brut!");
  const errorNotify = () =>
    hotToast.error(<div>Erreur: Exemple avec un div</div>);

  if (!session || session.notLoggedIn) {
    return (
      <div className="d-grid min-vh-100 align-items-center justify-content-center">
        <Link to="/auth/login" target="_blank" rel="noreferrer">
          S'identifier sur le backend...
        </Link>
      </div>
    );
  }

  return (
    <div className="App">
      <div>
        <HotToast />
      </div>
      <Header is1d={is1d} src={`${basePath}/img/illustrations/logo.png`} />
      <Main
        className={clsx("container-fluid bg-white", {
          "rounded-4 border": is1d,
          "mt-24": is1d,
        })}
      >
        <ExplorerProvider
          params={params}
          types={[RESOURCE.BLOG]}
          i18n={i18n}
          session={session}
          http={http}
          app={app}
        >
          <Explorer currentLanguage={currentLanguage} />
        </ExplorerProvider>

        <div className="d-block p-24">
          <hr />
          <h2>React Hot Toast 🍞</h2>
          <div className="d-flex gap-8 p-24">
            <Button color="tertiary" onClick={sucessNotify}>
              Make me a sucess toast
            </Button>
            <Button color="danger" onClick={errorNotify}>
              Make me a error toast
            </Button>
            <Button color="secondary" onClick={infoNotify}>
              Make me a info toast
            </Button>
            <Button color="primary" onClick={warningNotify}>
              Make me a warning toast
            </Button>
          </div>
        </div>
      </Main>
    </div>
  );
}

export default App;

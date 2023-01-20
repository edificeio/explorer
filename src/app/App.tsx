import { useEffect, useLayoutEffect } from "react";

import { ExplorerProvider } from "@contexts/ExplorerContext/ExplorerContext";
import { useOdeBackend } from "@hooks/useOdeBackend";
import { Header, Main } from "@ode-react-ui/core";
import { useOdeBootstrap } from "@ode-react-ui/hooks";
import { clsx } from "@shared/config/index";
import { OdeProviderParams } from "@shared/types";
import {
  Platform,
  useIs1d,
  useSet1d,
  useSetCurrentApp,
} from "@store/useOdeStore";
import { RESOURCE } from "ode-ts-client";
import { Link } from "react-router-dom";

import Explorer from "./Explorer";

function App({ params }: { params: OdeProviderParams }) {
  const is1d = useIs1d();
  const set1d = useSet1d();
  const setCurrentApp = useSetCurrentApp();

  const { getDegreeSchool, getOdeBoostrapThemePath } = useOdeBootstrap();

  const { session, explorer } = useOdeBackend(
    params.version || null,
    params.cdnDomain || null,
  );

  useLayoutEffect(() => {
    (async () => {
      const response = await getDegreeSchool();
      set1d(response);
    })();
  }, []);

  useLayoutEffect(() => {
    (async () => {
      const response = await getOdeBoostrapThemePath();

      const link = document.getElementById("theme") as HTMLAnchorElement;
      link.href = response;
    })();
  }, []);

  useEffect(() => {
    const initOnce = async () => {
      try {
        await Promise.all([
          Platform.apps.initialize(params.app, params.alternativeApp),
          Platform.apps.getWebAppConf(params.app),
        ]).then((results) => {
          if (results?.[1]) {
            setCurrentApp(results[1]);
          }
        });
      } catch (e) {
        console.log(e);
      }
    };
    initOnce();
  }, []);

  const themePath = Platform.theme.basePath as string;

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
      <Header is1d={is1d} src={`${themePath}/img/illustrations/logo.png`} />
      <Main
        className={clsx("container-fluid bg-white", {
          "rounded-4 border": is1d,
          "mt-24": is1d,
        })}
      >
        <ExplorerProvider
          explorer={explorer}
          params={params}
          types={[RESOURCE.BLOG]}
        >
          <Explorer />
        </ExplorerProvider>
      </Main>
    </div>
  );
}

export default App;

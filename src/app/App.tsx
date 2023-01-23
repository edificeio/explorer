import { useEffect, useLayoutEffect } from "react";

import { ExplorerProvider } from "@contexts/ExplorerContext/ExplorerContext";
import { useOdeBackend } from "@hooks/useOdeBackend";
import { Header, Main } from "@ode-react-ui/core";
import { useOdeBootstrap } from "@ode-react-ui/hooks";
import { clsx } from "@shared/config/index";
import { OdeProviderParams } from "@shared/types";
import {
  explorerFramework,
  Platform,
  useIs1d,
  useOdeStore,
  usePreviousId,
  useSession,
  useSetCurrentApp,
  useSetIs1d,
  useSetSession,
  useSetUser,
} from "@store/useOdeStore";
import { RESOURCE } from "ode-ts-client";
import { Link } from "react-router-dom";

import Explorer from "./Explorer";

function App({ params }: { params: OdeProviderParams }) {
  /* useOdeStore(); */

  const is1d = useIs1d();
  /* const setIs1d = useSetIs1d();
  const setCurrentApp = useSetCurrentApp(); */
  const setUser = useSetUser();
  const session = useSession();
  const setSession = useSetSession();
  const previousId = useOdeStore((state) => state.previousId);

  /* const { getDegreeSchool, getOdeBoostrapThemePath } = useOdeBootstrap(); */

  useOdeBackend(params.version || null, params.cdnDomain || null);

  useEffect(() => {
    setUser();
  }, []);

  /* useEffect(() => {
    (async () => {
      Promise.all([Platform.theme.onFullyReady()]).then((values) => {
        console.log("values", values[0]);
        console.log("values 2D", values[0].is2D);
        console.log("values", values[0].is1D);
      });
    })();
  }, []); */

  // useLayoutEffect(() => {
  //   (async () => {
  //     try {
  //       await Promise.all([
  //         Platform.apps.getWebAppConf(params.app),
  //         getDegreeSchool(),
  //         getOdeBoostrapThemePath(),
  //       ]).then((values) => {
  //         const [app, is1d, bootstrapPath] = values;

  //         setCurrentApp(app);
  //         setIs1d(is1d);

  //         const link = document.getElementById("theme") as HTMLAnchorElement;
  //         link.href = bootstrapPath;

  //         /* const [is1d, bootstrapPath] = values;
  //         setIs1d(is1d);

  //         const link = document.getElementById("theme") as HTMLAnchorElement;
  //         link.href = bootstrapPath; */
  //       });
  //     } catch (e) {
  //       // TODO: Show error with Toast
  //       console.log(e);
  //     }
  //   })();
  // }, []);

  /* useEffect(() => {
    (async () => {
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
        // TODO: Show error with Toast
        console.log(e);
      }
    })();
  }, []); */

  useEffect(() => {
    setSession();
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
          explorerFramework={explorerFramework}
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

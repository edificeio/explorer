import {
  useMemo,
  createContext,
  useContext,
  useEffect,
  useState,
  useLayoutEffect,
} from "react";

import useOdeBackend from "@hooks/useOdeBackend/useOdeBackend";
import { useOdeBootstrap } from "@ode-react-ui/hooks";
import { IIdiom, IWebApp } from "ode-ts-client";

import { OdeContextProps, OdeProviderProps } from "./types";

const OdeContext = createContext<OdeContextProps | null>(null!);

function OdeProvider({ children, params }: OdeProviderProps) {
  /* Hooks */
  const { session, configure, notif, explorer, http, login, logout } =
    useOdeBackend(params.version || null, params.cdnDomain || null);

  const { getDegreeSchool, getOdeBoostrapThemePath } = useOdeBootstrap();

  const { Platform } = configure;

  /* States */
  const [idiom, setIdiom] = useState<IIdiom>(Platform.idiom);
  const [currentApp, setCurrentApp] = useState<IWebApp>(null!);
  const [is1D, setIs1D] = useState<boolean>(false);

  useLayoutEffect(() => {
    (async () => {
      const resDegreeSchool = await getDegreeSchool();
      const resBoostrapSkin = await getOdeBoostrapThemePath();

      const link = document.getElementById("theme") as HTMLAnchorElement;
      link.href = resBoostrapSkin;

      setIs1D(resDegreeSchool);
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
        setIdiom(Platform.idiom); // ...same object, but triggers React.
      } catch (e) {
        console.log(e);
      }
    };
    initOnce();
  }, []);

  /* Computed properties => Based on a state */
  const appCode = currentApp?.address.replace("/", "");
  const themeBasePath = Platform.theme.basePath;

  const values = useMemo(
    () => ({
      configure,
      currentApp,
      appCode,
      explorer,
      http,
      idiom,
      login,
      logout,
      notif,
      params,
      session,
      is1D,
      themeBasePath,
    }),
    [
      configure,
      currentApp,
      appCode,
      explorer,
      http,
      idiom,
      login,
      logout,
      notif,
      params,
      session,
      is1D,
      themeBasePath,
    ],
  );

  return <OdeContext.Provider value={values}>{children}</OdeContext.Provider>;
}

function useOdeContext() {
  const context = useContext(OdeContext);

  if (!context) {
    throw new Error(`Cannot be used outside of OdeProvider`);
  }
  return context;
}

export { useOdeContext, OdeProvider };

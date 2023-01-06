import {
  useMemo,
  createContext,
  useContext,
  useEffect,
  useState,
  useLayoutEffect,
} from "react";

import useOdeBackend from "@hooks/useOdeBackend/useOdeBackend";
import { useThemeHelper } from "@ode-react-ui/hooks";
import { IIdiom, IWebApp } from "ode-ts-client";

import { OdeContextProps, OdeProviderProps } from "./types";

const OdeContext = createContext<OdeContextProps | null>(null!);

export default function OdeProvider({ children, params }: OdeProviderProps) {
  /* Hooks */
  const { session, configure, notif, explorer, http, login, logout } =
    useOdeBackend(params.version || null, params.cdnDomain || null);

  const { getDegreeSchool, getBootstrapSkinPath, getTheme } = useThemeHelper();

  const { Platform } = configure;

  /* States */
  const [idiom, setIdiom] = useState<IIdiom>(Platform.idiom);
  const [currentApp, setCurrentApp] = useState<IWebApp>(null!);
  const [is1D, setIs1D] = useState<boolean>(false);
  const [theme, setTheme] = useState({});

  const appCode = currentApp?.address.replace("/", "");

  useEffect(() => {
    console.log("OdeContext INIT ONLY ONCE, PLEASE !");

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

  useEffect(() => {
    (async () => {
      const response = await getDegreeSchool();
      setIs1D(response);

      const theme = await getTheme();
      setTheme(theme);
    })();
  }, []);

  useLayoutEffect(() => {
    (async () => {
      const response = await getBootstrapSkinPath();

      const link = document.getElementById("theme") as HTMLAnchorElement;
      link.href = `${response}/theme.css`;
    })();
  }, []);

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
      theme,
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
      theme,
    ],
  );

  return <OdeContext.Provider value={values}>{children}</OdeContext.Provider>;
}

export const useOdeContext = () => {
  const context = useContext(OdeContext);

  if (!context) {
    throw new Error(`Cannot be used outside of OdeProvider`);
  }
  return context;
};

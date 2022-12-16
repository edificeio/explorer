import {
  useMemo,
  createContext,
  useContext,
  useEffect,
  ReactNode,
  useState,
} from "react";

import useOdeBackend from "@hooks/useOdeBackend";
import {
  App,
  IConfigurationFramework,
  IExplorerFramework,
  IHttp,
  IIdiom,
  INotifyFramework,
  ISession,
  IWebApp,
} from "ode-ts-client";

interface OdeContextProps {
  session: ISession | null;
  configure: IConfigurationFramework;
  notif: INotifyFramework;
  explorer: IExplorerFramework;
  http: IHttp;
  idiom: IIdiom;
  params: OdeProviderParams;
  currentApp: IWebApp;
  login: () => void;
  logout: () => void;
}
const OdeContext = createContext<OdeContextProps | null>(null!);

export type OdeProviderParams = {
  app: App;
  alternativeApp?: boolean;
  version?: string | null;
  cdnDomain?: string | null;
};

interface OdeProviderProps {
  children: ReactNode;
  params: OdeProviderParams;
}

type Apps = {
  [key: string]: any;
};

export default function OdeProvider({ children, params }: OdeProviderProps) {
  const { session, configure, notif, explorer, http, login, logout } =
    useOdeBackend(params.version || null, params.cdnDomain || null);

  const [idiom, setIdiom] = useState<IIdiom>(configure.Platform.idiom);

  const [currentApp, setCurrentApp] = useState<IWebApp>(null!);

  function getCurrentApp(list: [], param: App) {
    const { apps }: Apps = list;
    const filteredList = apps.filter((item: any) => {
      const prefix = item.prefix.replace("/", "");
      return prefix === param;
    });
    const [app] = filteredList;
    return app;
  }

  useEffect(() => {
    console.log("OdeContext INIT ONLY ONCE, PLEASE !");
    const initOnce = async () => {
      try {
        console.log(`init ${params.app}`);
        await configure.Platform.apps.initialize(
          params.app,
          params.alternativeApp,
        );
        const appsList = await configure.Platform.apps.getAppsList();
        setCurrentApp(getCurrentApp(appsList, params.app));
        setIdiom(configure.Platform.idiom); // ...same object, but triggers React.
      } catch (e) {
        console.log(e);
      }
    };
    initOnce();
  }, []);

  const values = useMemo(
    () => ({
      configure,
      currentApp,
      explorer,
      http,
      idiom,
      login,
      logout,
      notif,
      params,
      session,
    }),
    [session, idiom, currentApp],
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

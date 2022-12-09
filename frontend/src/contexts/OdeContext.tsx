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
} from "ode-ts-client";

interface OdeContextProps {
  session: ISession | null;
  configure: IConfigurationFramework;
  notif: INotifyFramework;
  explorer: IExplorerFramework;
  http: IHttp;
  idiom: IIdiom;
  params: OdeProviderParams;
  login: () => void;
  logout: () => void;
}
const OdeContext = createContext<OdeContextProps | null>(null!);

export type OdeProviderParams = {
  app: App;
  version?: string | null;
  cdnDomain?: string | null;
};

interface OdeProviderProps {
  children: ReactNode;
  params: OdeProviderParams;
}

export default function OdeProvider({ children, params }: OdeProviderProps) {
  const { session, configure, notif, explorer, http, login, logout } =
    useOdeBackend(params.version || null, params.cdnDomain || null);

  const [idiom, setIdiom] = useState<IIdiom>(configure.Platform.idiom);

  useEffect(() => {
    console.log("OdeContext INIT ONLY ONCE, PLEASE !");
    const initOnce = async () => {
      try {
        console.log(`initizaling ${params.app}`);
        await configure.Platform.apps.initialize(params.app);
        setIdiom(configure.Platform.idiom); // ...same object, but triggers React.
      } catch (e) {
        console.log(e);
      }
    };
    initOnce();
  }, []);

  const values = useMemo(
    () => ({
      session,
      configure,
      notif,
      explorer,
      http,
      idiom,
      params,
      login,
      logout,
    }),
    [session, idiom],
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

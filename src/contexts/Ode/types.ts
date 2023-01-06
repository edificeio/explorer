import { ReactNode } from "react";

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

export interface OdeProviderParams {
  app: App;
  alternativeApp?: boolean;
  version?: string | null;
  cdnDomain?: string | null;
}

export interface OdeProviderProps {
  children: ReactNode;
  params: OdeProviderParams;
}

export interface OdeContextProps {
  session: ISession | null;
  configure: IConfigurationFramework;
  notif: INotifyFramework;
  explorer: IExplorerFramework;
  http: IHttp;
  idiom: IIdiom;
  params: OdeProviderParams;
  currentApp: IWebApp;
  appCode?: string;
  is1D: boolean;
  theme: Record<string, string>;
  login: () => void;
  logout: () => void;
}

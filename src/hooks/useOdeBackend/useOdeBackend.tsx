/* eslint-disable @typescript-eslint/restrict-template-expressions */
import { useEffect, useState } from "react";

import { OdeProviderParams } from "@shared/types";
import {
  ConfigurationFrameworkFactory,
  ExplorerFrameworkFactory,
  IIdiom,
  ISession,
  ITheme,
  IWebApp,
  NotifyFrameworkFactory,
  SessionFrameworkFactory,
  TransportFrameworkFactory,
} from "ode-ts-client";

/** Custom Hook for ode-ts-client integration */
export default function useOdeBackend(params: OdeProviderParams) {
  const sessionFramework = SessionFrameworkFactory.instance();
  const configureFramework = ConfigurationFrameworkFactory.instance();
  const explorerFramework = ExplorerFrameworkFactory.instance();
  const notifyFramework = NotifyFrameworkFactory.instance();
  const { http } = TransportFrameworkFactory.instance();

  const [app, setApp] = useState<IWebApp>();
  const [currentLanguage, setCurrentLanguage] = useState<string>("fr");
  const [idiom, setIdiom] = useState<IIdiom>(configureFramework.Platform.idiom);
  const [session, setSession] = useState<ISession | null>(null);
  const [theme, setTheme] = useState<ITheme>(configureFramework.Platform.theme);

  function setBootstrapTheme(conf: any) {
    let odeBootstrapPath: string = "";

    for (const override of conf.overriding) {
      if (override.child === configureFramework.Platform.theme.themeName) {
        odeBootstrapPath = `${configureFramework.Platform.cdnDomain}/assets/themes/`;
        odeBootstrapPath += `${override.bootstrapVersion}/skins/${configureFramework.Platform.theme.skinName}/theme.css`;
      }
    }
    const link = document.getElementById("theme") as HTMLAnchorElement;
    link.href = odeBootstrapPath;
  }

  useEffect(() => {
    (async () => {
      try {
        await Promise.all([
          sessionFramework.initialize(),
          configureFramework.initialize(
            params.version || null,
            params.cdnDomain || null,
          ),
        ]);

        setSession(sessionFramework.session);

        const promise = await Promise.all([
          configureFramework.Platform.apps.initialize(
            params.app,
            params.alternativeApp,
          ),
          configureFramework.Platform.apps.getWebAppConf(params.app),
          configureFramework.Platform.theme.getConf(),
        ]);
        setApp(promise[1]);
        setBootstrapTheme(promise[2]);
        setTheme(configureFramework.Platform.theme);
        setIdiom(configureFramework.Platform.idiom);
      } catch (e) {
        console.log(e); // An unrecovable error occured
      }
    })();
  }, []);

  useEffect(() => {
    setCurrentLanguage(session?.currentLanguage as string);
  }, [session]);

  /** The custom-hook-ized login process */
  function login(/* email: string, password: string */) {
    // sessionFramework.login(email, password).then(() => {
    //   setSession(sessionFramework.session); // ...same session object, but triggers React rendering.
    // });
  }

  /** The custom-hook-ized logout process */
  function logout() {
    // sessionFramework.logout().then(() => {
    //   setSession(sessionFramework.session); // ...same session object, but triggers React rendering.
    // });
  }

  // Return instances, to be initialized later.
  return {
    login,
    logout,
    session,
    configureFramework,
    notifyFramework,
    explorerFramework,
    http,
    theme,
    app,
    appName: params.app,
    currentLanguage,
    i18n: idiom.translate,
  };
}

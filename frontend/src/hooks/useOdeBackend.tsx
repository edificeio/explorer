import { useEffect, useState } from "react";

import {
  ConfigurationFrameworkFactory,
  ExplorerFrameworkFactory,
  ISession,
  NotifyFrameworkFactory,
  SessionFrameworkFactory,
  TransportFrameworkFactory,
} from "ode-ts-client";

/** Custom Hook for ode-ts-client integration */
export default function useOdeBackend(
  version: string | null,
  cdnDomain: string | null,
) {
  const sessionFramework = SessionFrameworkFactory.instance();
  const configure = ConfigurationFrameworkFactory.instance();
  const explorer = ExplorerFrameworkFactory.instance();
  const notif = NotifyFrameworkFactory.instance();
  const { http } = TransportFrameworkFactory.instance();

  const [session, setSession] = useState<ISession | null>(
    sessionFramework.session,
  );

  // Exécuté au premier render du Composant
  useEffect(() => {
    console.log("useOdeBackend INIT ONLY ONCE, PLEASE !");
    const initOnce = async () => {
      try {
        await sessionFramework.initialize();
        await configure.initialize(version, cdnDomain);
        setSession(sessionFramework.session); // ...same session object, but triggers React rendering.
      } catch (e) {
        setSession(null); // An unrecovable error occured
      }
    };
    initOnce();
  }, []);

  /** The custom-hook-ized login process */
  function login(/* email: string, password: string */) {
    alert("login clicked !");
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
    configure,
    explorer,
    notif,
    http,
  };
}

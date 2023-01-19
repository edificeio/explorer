import { useEffect, useState } from "react";

import {
  ConfigurationFrameworkFactory,
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
  const notif = NotifyFrameworkFactory.instance();
  const { http } = TransportFrameworkFactory.instance();

  const [session, setSession] = useState<ISession | null>(null);

  // Exécuté au premier render du Composant
  useEffect(() => {
    const initOnce = async () => {
      try {
        await sessionFramework.initialize();
        await configure.initialize(version, cdnDomain);
        setSession(sessionFramework.session); // ...same object, but triggers React.
      } catch (e) {
        console.log(e); // An unrecovable error occured
      }
    };
    initOnce();
  }, []);

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
    configure,
    notif,
    http,
  };
}

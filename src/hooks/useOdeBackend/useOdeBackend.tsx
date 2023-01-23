import { useEffect } from "react";

import { ConfigurationFrameworkFactory } from "ode-ts-client";

/** Custom Hook for ode-ts-client integration */
export default function useOdeBackend(
  version: string | null,
  cdnDomain: string | null,
) {
  const configureExplorer = ConfigurationFrameworkFactory.instance();

  // Exécuté au premier render du Composant
  useEffect(() => {
    const initOnce = async () => {
      try {
        await configureExplorer.initialize(version, cdnDomain);
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
    configureExplorer,
  };
}

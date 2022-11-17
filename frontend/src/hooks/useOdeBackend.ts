import { useEffect, useState } from "react";

import {
  ConfigurationFrameworkFactory,
  ExplorerFrameworkFactory,
  ISession,
  SessionFrameworkFactory,
} from "ode-ts-client";

/** Custom Hook for ode-ts-client integration */
export default function useOdeBackend(
  version: string | null,
  cdnDomain: string | null,
) {
  const [session, setSession] = useState<ISession | null>(
    SessionFrameworkFactory.instance().session,
  );

  // Exécuté au premier render du Composant
  useEffect(() => {
    console.log("useOdeBackend INIT ONLY ONCE, PLEASE !");

    const initOnce = async () => {
      try {
        await SessionFrameworkFactory.instance().initialize();
        await ConfigurationFrameworkFactory.instance().initialize(
          version,
          cdnDomain,
        );
        setSession(SessionFrameworkFactory.instance().session); // ...same object, but triggers React rendering.
      } catch (e) {
        setSession(null);
      }
    };
    initOnce();
  }, []);

  const configure = ConfigurationFrameworkFactory.instance();
  const explorer = ExplorerFrameworkFactory.instance();
  // ...

  // Return instances, to be initialized later.
  return {
    session,
    configure,
    explorer,
  };
}

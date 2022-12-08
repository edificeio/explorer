import { useMemo, createContext, useContext, ReactNode } from "react";

import useOdeBackend from "@hooks/useOdeBackend";

interface OdeContextProps {
  session: any;
}

const OdeContext = createContext<OdeContextProps | null>(null!);

export type OdeProviderParams = {
  app: string;
  version?: string | null;
  cdnDomain?: string | null;
};

export default function OdeProvider({
  children,
  params,
}: {
  params: OdeProviderParams;
  children: ReactNode;
}) {
  //   const { session, login, logout } = useOdeBackend(null, null);
  const { session } = useOdeBackend(
    params.version || null,
    params.cdnDomain || null,
  );

  const values = useMemo(
    () => ({
      session,
    }),
    [],
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

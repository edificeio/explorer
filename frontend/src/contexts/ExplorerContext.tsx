import { useMemo, createContext, useContext, ReactNode } from "react";

import {
  IExplorerContext,
  IExplorerFramework,
  ResourceType,
} from "ode-ts-client";

import { useOdeContext } from "./OdeContext";

interface ExplorerContextProps {
  explorer: IExplorerFramework;
  context: IExplorerContext;
}

const ExplorerContext = createContext<ExplorerContextProps | null>(null!);

export default function ExplorerContextProvider({
  children,
  types,
}: {
  children: ReactNode;
  types: ResourceType[];
}) {
  const { params, explorer, session } = useOdeContext();

  const context = explorer.createContext(types, params.app);

  const values = useMemo(
    () => ({
      explorer,
      context,
    }),
    [session],
  );

  return (
    <ExplorerContext.Provider value={values}>
      {children}
    </ExplorerContext.Provider>
  );
}

export const useExplorerContext = () => {
  const context = useContext(ExplorerContext);

  if (!context) {
    throw new Error(`Cannot be used outside of ExplorerContextProvider`);
  }
  return context;
};

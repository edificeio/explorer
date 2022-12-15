import {
  useMemo,
  createContext,
  useContext,
  ReactNode,
  useEffect,
} from "react";

import {
  ACTION,
  IExplorerContext,
  IExplorerFramework,
  ResourceType,
} from "ode-ts-client";

import { useOdeContext } from "./OdeContext";

interface ExplorerContextProps {
  explorer: IExplorerFramework;
  context: IExplorerContext;
  onOpen: (assetId: string) => void;
  onCreate: () => void;
}

interface ExplorerProviderProps {
  children: ReactNode;
  types: ResourceType[];
}

const ExplorerContext = createContext<ExplorerContextProps | null>(null!);

export default function ExplorerContextProvider({
  children,
  types,
}: ExplorerProviderProps) {
  const { params, explorer } = useOdeContext();

  const context = explorer.createContext(types, params.app);

  function onOpen(assetId: string) {
    return explorer
      .getBus()
      .publish(types[0], ACTION.OPEN, { resourceId: assetId });
  }

  function onCreate() {
    return explorer.getBus().publish(types[0], ACTION.CREATE, "test proto");
  }

  const values = useMemo(
    () => ({
      explorer,
      context,
      onOpen,
      onCreate,
    }),
    [],
  );

  // Observe streamed search results
  useEffect(() => {
    const subscription = context.latestResources().subscribe({
      next: (resultset) => {
        // Prepare searching next page
        const { pagination } = context.getSearchParameters();
        pagination.maxIdx = resultset.output.pagination.maxIdx;
        pagination.startIdx =
          resultset.output.pagination.startIdx +
          resultset.output.pagination.pageSize;
        if (
          typeof pagination.maxIdx !== "undefined" &&
          pagination.startIdx > pagination.maxIdx
        ) {
          pagination.startIdx = pagination.maxIdx;
        }
      },
    });

    return () => {
      if (subscription) {
        subscription.unsubscribe();
      }
    };
  }, []); // execute effect only once

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

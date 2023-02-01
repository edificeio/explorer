import {
  ReactNode,
  useContext,
  createContext,
  Context,
  useEffect,
} from "react";

import { ExplorerStore, ExplorerStoreProps } from "@store/explorer/types";
import { createExplorerStore } from "@store/explorer/useExplorerStore";
import { StoreApi, useStore } from "zustand";

const ExplorerContext: Context<StoreApi<ExplorerStore>> = createContext<
  StoreApi<ExplorerStore>
>(null!);
const explorerStore: StoreApi<ExplorerStore> = createExplorerStore();

export function ExplorerProvider({
  children,
  ...params
}: ExplorerStoreProps & { children: ReactNode }) {
  // init store
  useEffect(() => {
    explorerStore.getState().init(params);
  }, [params.app, params.i18n]);

  // return provider
  return (
    <ExplorerContext.Provider value={explorerStore}>
      {children}
    </ExplorerContext.Provider>
  );
}
export function useExplorerContext() {
  if (!ExplorerContext) {
    throw new Error(`ExplorerContext not initialized`);
  }
  const context = useStore(useContext(ExplorerContext));
  if (!context) {
    throw new Error(`Cannot be used outside of ExplorerContextProvider`);
  }
  return context;
}

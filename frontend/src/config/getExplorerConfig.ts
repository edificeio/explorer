import {
  type App,
  type ResourceType,
  type IAction,
  type IFilter,
  type IOrder,
} from "edifice-ts-client";

export interface AppParams {
  app: App;
  types: ResourceType[];
  filters: IFilter[];
  orders: IOrder[];
  actions: IAction[];
  trashActions: IAction[];
  libraryAppFilter?: string;
}

const rootElement = document.querySelector<HTMLElement>(
  "[data-explorer-config]",
);

let config: AppParams;

export function getExplorerConfig(): AppParams {
  if (rootElement?.dataset?.explorerConfig) {
    const { explorerConfig } = rootElement.dataset;
    // Inject params (JSON object or string) read from index.html in OdeProvider
    try {
      const parsedConfig = JSON.parse(explorerConfig);
      config = Object.assign({}, parsedConfig);
    } catch (e) {
      console.error(
        "[Explorer Config] could not parse app params from root data attributes:",
        rootElement?.dataset,
        e,
      );
    }
  }
  return config;
}

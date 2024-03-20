import {
  type App,
  type ResourceType,
  type IAction,
  type IFilter,
  type IOrder,
} from "edifice-ts-client";

export interface AppParams {
  /** Application code */
  app: App;
  /** Array of ResourceType */
  types: ResourceType[];
  /** Array of IFilter */
  filters: IFilter[];
  /** Array of IOrder */
  orders: IOrder[];
  /** Array of IAction */
  actions: IAction[];
  /** Array of trashable IAction */
  trashActions: IAction[];
  /** BPR application code */
  libraryAppFilter?: string;
  /** Enable or disable Explorer Onboarding Modal */
  enableOnboarding?: boolean;
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
      config = {
        ...parsedConfig,
        enableOnboarding:
          parsedConfig.enableOnboarding !== undefined
            ? parsedConfig.enableOnboarding
            : true,
      };
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

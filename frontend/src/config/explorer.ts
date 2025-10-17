import {
  IResource,
  ResourceService,
  type App,
  type IAction,
  type IFilter,
  type IOrder,
  type ResourceType,
} from '@edifice.io/client';
import { BlogResourceService } from '~/services/resource/service';

export interface AppParams {
  /** Application code */
  app: App;
  /**
   * Initialize App Resource Service
   */
  service: typeof ResourceService;
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
  /** Callback function triggered when a resource is created */
  onResourceCreated?: (resource: IResource) => void;
}

const rootElement = document.querySelector<HTMLElement>(
  '[data-explorer-config]',
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
        service: BlogResourceService,
        enableOnboarding:
          parsedConfig.enableOnboarding !== undefined
            ? parsedConfig.enableOnboarding
            : true,
      };
    } catch (e) {
      console.error(
        '[Explorer Config] could not parse app params from root data attributes:',
        rootElement?.dataset,
        e,
      );
    }
  }
  return config;
}

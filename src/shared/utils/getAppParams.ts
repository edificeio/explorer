import {
  APP,
  type BooleanFilterType,
  type SortByType,
  type App,
  type ResourceType,
} from "ode-ts-client";
import { type ShareRightActionDisplayNameExt } from "ode-ts-client/dist/services/ShareService";

const rootElement = document.querySelector<HTMLElement>("[data-ode-app]");
let _appParams: AppParams;
export function getAppParams(): AppParams {
  if (_appParams) {
    return _appParams;
  }
  _appParams = {
    app: APP.PORTAL,
    types: ["folder"],
    filters: [],
    actions: [],
    orders: [],
  };
  if (rootElement?.dataset?.odeApp) {
    const { odeApp } = rootElement.dataset;
    // Inject params (JSON object or string) read from index.html in OdeProvider
    try {
      const p = JSON.parse(odeApp);
      Object.assign(_appParams, p);
    } catch (e) {
      console.error(
        "[AppParams] could not parse app params from root data attributes:",
        rootElement?.dataset,
        e,
      );
    }
  } else {
    console.error(
      "[AppParams] could not found app params from root data attributes:",
      rootElement?.dataset,
    );
  }
  return _appParams;
}

export interface AppParams {
  app: App;
  types: ResourceType[];
  filters: IFilter[];
  orders: IOrder[];
  actions: IAction[];
}
export type IActionType =
  | "open"
  | "edit"
  | "create"
  | "createPublic"
  | "move"
  | "delete"
  | "publish"
  | "print"
  | "share";
export interface IAction {
  id: IActionType;
  available: boolean;
  target?: "actionbar" | "tree";
  workflow: string;
  right?: ShareRightActionDisplayNameExt;
}

export interface IOrder {
  id: SortByType;
  i18n: string;
  defaultValue?: boolean;
}

export interface IFilter {
  id: BooleanFilterType | string;
  defaultValue?: string | string[] | boolean | boolean[];
  values?: string[];
}

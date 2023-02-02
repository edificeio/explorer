import { ExplorerFrameworkFactory } from "ode-ts-client";
import { createStore } from "zustand";

import { withDefaultState } from "./state";
import { ExplorerAction, ExplorerState } from "./types";
import { withAppActions } from "./withAppActions";
import { withGetters } from "./withGetters";
import { withListActions } from "./withListActions";
import { withListView } from "./withListView";
import { withTreeView } from "./withTreeView";

const BUS = ExplorerFrameworkFactory.instance().getBus();
const TOAST_DELAY = 5000;
const PAGE_SIZE = 4;

export const createExplorerStore = () =>
  createStore<ExplorerState & ExplorerAction>((set, get) => ({
    ...withDefaultState({ pageSize: PAGE_SIZE }),
    ...withListActions({ bus: BUS, toastDelay: TOAST_DELAY, get, set }),
    ...withListView({ bus: BUS, toastDelay: TOAST_DELAY, get, set }),
    ...withTreeView({ bus: BUS, toastDelay: TOAST_DELAY, get, set }),
    ...withGetters({ get }),
    ...withAppActions({ get, bus: BUS, set, toastDelay: TOAST_DELAY }),
  }));

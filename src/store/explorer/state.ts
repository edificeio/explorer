import { FOLDER } from "ode-ts-client";

import { ExplorerState } from "./types";

export const withDefaultState = ({
  pageSize,
  ...props
}: {
  pageSize: number;
}): ExplorerState => ({
  ...props,
  ready: false,
  selectedNodeIds: [],
  actions: [],
  filters: [],
  orders: [],
  preferences: undefined,
  searchParams: {
    app: undefined!,
    types: [],
    filters: {},
    pagination: {
      startIdx: 0,
      pageSize,
    },
  },
  treeData: {
    id: FOLDER.DEFAULT,
    name: "explorer.filters.mine",
    section: true,
    children: [],
  },
  folderList: [],
  resourceList: [],
  selectedFolders: [],
  selectedResources: [],
  notifications: [],
  app: undefined,
  http: undefined!,
  i18n: undefined!,
  params: undefined!,
  session: undefined!,
  types: [],
  treeviewStatus: undefined,
});

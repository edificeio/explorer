import { TreeNodeFolderWrapper } from "@features/Explorer/adapters";
import { type TreeNode } from "@ode-react-ui/advanced";
import { type OdeProviderParams } from "@ode-react-ui/core";
import { translate } from "@shared/constants";
import { deleteNode } from "@shared/utils/deleteNode";
import {
  getAppParams,
  type IAction,
  type IOrder,
  type IFilter,
} from "@shared/utils/getAppParams";
import { moveNode } from "@shared/utils/moveNode";
import { wrapTreeNode } from "@shared/utils/wrapTreeNode";
import {
  type IPreferences,
  type ISearchParameters,
  FOLDER,
  type MoveParameters,
  type DeleteParameters,
  type IResource,
  type ResourceType,
  type PublishParameters,
  type IFolder,
  type IActionResult,
  odeServices,
} from "ode-ts-client";
import { type StateCreator } from "zustand";

import { type State } from ".";

type Thing = "folder" | "resource" | "all";
type PromiseVoid = Promise<void>;

export interface ExplorerSlice {
  isAppReady: boolean;
  actions: Array<IAction & { available: boolean }>;
  filters: IFilter[];
  orders: IOrder[];
  preferences?: IPreferences;
  searchParams: ISearchParameters;
  notifications: Notification[];
  init: (params: OdeProviderParams) => PromiseVoid;
  getHasResourcesOrFolders: () => number;
  moveSelectedTo: (destinationId: string) => PromiseVoid;
  deleteSelection: () => PromiseVoid;
  publish: (
    type: ResourceType,
    params: PublishParameters,
  ) => Promise<IActionResult | undefined>;
  reloadListView: () => PromiseVoid;
  clearListView: () => void;
  select: (id: string[], type: Thing) => void;
  deselect: (id: string[], type: Thing) => void;
  deselectAll: (type: Thing) => void;
  isActionAvailable: (action: "create" | "publish") => boolean;
}

// * https://docs.pmnd.rs/zustand/guides/typescript#slices-pattern
export const createExplorerSlice: StateCreator<State, [], [], ExplorerSlice> = (
  set,
  get,
) => ({
  isAppReady: false,
  actions: [],
  filters: [],
  orders: [],
  notifications: [],
  preferences: undefined,
  searchParams: {
    app: undefined!,
    types: [],
    filters: {},
    pagination: {
      startIdx: 0,
      pageSize: 4,
    },
  },
  init: async (_: OdeProviderParams) => {
    const { app, types } = getAppParams();

    try {
      // get context from backend
      const { searchParams: previousParam, getCurrentFolderId } = get();
      const searchParams: ISearchParameters = {
        ...previousParam,
        orders: { updatedAt: "desc" },
        app,
        types,
      };

      const trashed = getCurrentFolderId() === FOLDER.BIN;
      const { folders, resources, preferences, pagination } = await odeServices
        .resource(searchParams.app)
        .createContext({ ...searchParams, trashed });

      console.log(
        await odeServices
          .resource(searchParams.app)
          .createContext({ ...searchParams, trashed }),
      );

      const { actions, filters, orders } = getAppParams();
      const actionRights = actions.map((a) => a.workflow);
      const availableRights = await odeServices
        .rights()
        .sessionHasWorkflowRights(actionRights);
      const currentMaxIdx = pagination.startIdx + pagination.pageSize - 1;
      const hasMoreResources = currentMaxIdx < (pagination.maxIdx || 0);
      set((state) => ({
        ...state,
        isAppReady: true,
        actions: actions.map((a) => ({
          ...a,
          available: availableRights[a.workflow],
        })),
        preferences,
        orders,
        filters,
        folders,
        resources,
        searchParams,
        hasMoreResources,
        treeData: {
          ...state.treeData,
          children: folders.map((folder) => new TreeNodeFolderWrapper(folder)),
          name: translate(state.treeData.name),
        },
      }));
    } catch (error) {
      // if failed push error
      console.error("explorer init failed: ", error);
    }
  },
  getHasResourcesOrFolders() {
    const { resources, folders } = get();
    return resources?.length || folders.length;
  },
  moveSelectedTo: async (destinationId: string) => {
    try {
      const { selectedFolders, selectedResources, searchParams } = get();
      const parameters: MoveParameters = {
        application: searchParams.app,
        folderId: destinationId,
        resourceIds: selectedResources,
        folderIds: selectedFolders,
      };
      await odeServices.resource(searchParams.app).moveToFolder(parameters);
      set((state) => {
        const treeData: TreeNode = moveNode(state.treeData, {
          destinationId,
          folders: selectedFolders,
        });
        const resources = state.resources.filter(
          (resource: IResource) => !selectedResources.includes(resource.id),
        );
        const folders = state.folders.filter(
          (folder: IFolder) => !selectedFolders.includes(folder.id),
        );
        return { ...state, folders, resources, treeData };
      });
    } catch (error) {
      // if failed push error
      console.error("explorer move failed: ", error);
    }
  },
  deleteSelection: async () => {
    try {
      const { selectedFolders, selectedResources, searchParams } = get();
      const parameters: DeleteParameters = {
        application: searchParams.app,
        resourceType: searchParams.types[0],
        resourceIds: selectedResources,
        folderIds: selectedFolders,
      };
      await odeServices.resource(searchParams.app).deleteAll(parameters);
      set((state) => {
        const treeData: TreeNode = deleteNode(state.treeData, {
          folders: selectedFolders,
        });
        const resources = state.resources.filter(
          (resource: IResource) => !selectedResources.includes(resource.id),
        );
        const folders = state.folders.filter(
          (folder: IFolder) => !selectedFolders.includes(folder.id),
        );
        return { ...state, folders, resources, treeData };
      });
    } catch (error) {
      // if failed push error
      console.error("explorer delete failed: ", error);
    }
  },
  select: (id: string[], type: Thing) => {
    set((state) => {
      switch (type) {
        case "folder":
          return {
            ...state,
            selectedFolders: [...state.selectedFolders, ...id],
          };
        case "resource":
          return {
            ...state,
            selectedResources: [...state.selectedResources, ...id],
          };
        case "all":
          // should never be used?
          return {
            ...state,
            selectedFolders: [...state.selectedFolders, ...id],
            selectedResources: [...state.selectedResources, ...id],
          };
      }
    });
  },
  deselect: (id: string[], type: Thing) => {
    set((state) => {
      const selectedFolders = state.selectedFolders.filter(
        (folder) => !id.includes(folder),
      );
      const selectedResources = state.selectedResources.filter(
        (folder) => !id.includes(folder),
      );
      switch (type) {
        case "folder":
          return { ...state, selectedFolders, selectedResources };
        case "resource":
          return { ...state, selectedResources };
        case "all":
          // should never be used?
          return {
            ...state,
            selectedFolders,
            selectedResources,
          };
      }
    });
  },
  deselectAll: (type: Thing) => {
    set((state) => {
      switch (type) {
        case "folder":
          return { ...state, selectedFolders: [] };
        case "resource":
          return { ...state, selectedResources: [] };
        case "all":
          // should never be used?
          return {
            ...state,
            selectedFolders: [],
            selectedResources: [],
          };
      }
    });
  },
  reloadListView: async () => {
    try {
      const { clearListView, getCurrentFolderId } = get();
      // clear list view
      clearListView();
      // get params after clear
      const { searchParams } = get();
      // fetch subfolders
      const trashed = getCurrentFolderId() === FOLDER.BIN;
      const { folders, resources, pagination } = await odeServices
        .resource(searchParams.app)
        .searchContext({ ...searchParams, trashed });

      const currentMaxIdx = pagination.startIdx + pagination.pageSize - 1;
      const hasMoreResources = currentMaxIdx < (pagination.maxIdx || 0);
      set((state) => {
        return {
          ...state,
          resources,
          folders,
          treeData: wrapTreeNode(
            state.treeData,
            folders,
            searchParams.filters.folder || FOLDER.DEFAULT,
          ),
          searchParams: {
            ...state.searchParams,
            pagination,
          },
          hasMoreResources,
        };
      });
    } catch (error) {
      // if failed push error
      console.error("explorer refresh failed: ", error);
    }
  },
  publish: async (
    resourceType: ResourceType,
    params: PublishParameters,
  ): Promise<IActionResult | undefined> => {
    const { searchParams } = get();
    const tmp = await odeServices.resource(searchParams.app).publish(params);
    return tmp;
  },
  clearListView: () => {
    set((state) => {
      return {
        ...state,
        resources: [],
        folders: [],
        searchParams: {
          ...state.searchParams,
          pagination: {
            ...state.searchParams.pagination,
            startIdx: 0,
          },
        },
      };
    });
  },
  isActionAvailable: (action) => {
    const { actions } = get();
    const found = actions.filter((e) => e.id === action && e.available);
    return found.length > 0;
  },
});

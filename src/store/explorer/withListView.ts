import {
  ACTION,
  FOLDER,
  GetResourcesResult,
  IBus,
  IFolder,
  IResource,
  RESOURCE,
} from "ode-ts-client";

import {
  ElementType,
  GetExplorerStateFunction,
  SetExplorerStateFunction,
} from "./types";
import { addNotification, wrapTreeNode } from "./utils";

export const withListView = ({
  bus,
  toastDelay,
  get,
  set,
}: {
  bus: IBus;
  toastDelay: number;
  get: GetExplorerStateFunction;
  set: SetExplorerStateFunction;
}) => ({
  isFolderSelected(folder: IFolder) {
    const { selectedFolders } = get();
    return selectedFolders.includes(folder.id);
  },
  isResourceSelected: (res: IResource) => {
    const { selectedResources } = get();
    return Object.hasOwn(selectedResources, res.id);
  },
  getMoreResources: async () => {
    try {
      const { searchParams: searchParamsOrig } = get();
      const searchParams = { ...searchParamsOrig };
      const { pagination: oldPagination } = searchParams;
      // set new start idx and check maxidx
      oldPagination.startIdx = oldPagination.startIdx + oldPagination.pageSize;
      if (
        typeof oldPagination.maxIdx !== "undefined" &&
        oldPagination.startIdx > oldPagination.maxIdx
      ) {
        oldPagination.startIdx = oldPagination.maxIdx;
      }
      // call backend
      const { folders, resources, pagination } = (await bus.publish(
        RESOURCE.FOLDER,
        ACTION.SEARCH,
        searchParams,
      )) as GetResourcesResult;
      set((state) => {
        return {
          ...state,
          folderList: folders,
          resourceList: [...state.resourceList, ...resources],
          treeData: wrapTreeNode(
            state.treeData,
            folders,
            searchParams.filters.folder || FOLDER.DEFAULT,
          ),
          searchParams: {
            ...state.searchParams,
            pagination,
          },
        };
      });
    } catch (e) {
      // if failed push error
      console.error("explorer getmore failed: ", e);
      addNotification(
        { type: "error", message: "explorer.getmore.failed" },
        toastDelay,
        set,
      );
    }
  },
  reloadListView: async () => {
    try {
      const { clearListView } = get();
      // clear list view
      clearListView();
      // get params after clear
      const { searchParams } = get();
      // fetch subfolders
      const { folders, resources, pagination } = (await bus.publish(
        RESOURCE.FOLDER,
        ACTION.SEARCH,
        searchParams,
      )) as GetResourcesResult;
      set((state) => {
        return {
          ...state,
          resourceList: resources,
          folderList: folders,
          treeData: wrapTreeNode(
            state.treeData,
            folders,
            searchParams.filters.folder || FOLDER.DEFAULT,
          ),
          searchParams: {
            ...state.searchParams,
            pagination,
          },
        };
      });
    } catch (e) {
      // if failed push error
      console.error("explorer refresh failed: ", e);
      addNotification(
        { type: "error", message: "explorer.refresh.failed" },
        toastDelay,
        set,
      );
    }
  },
  select: (id: string[], type: ElementType) => {
    set(({ ...state }) => {
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
  deselect: (id: string[], type: ElementType) => {
    set((state) => {
      const selectedFolders = state.selectedFolders.filter(
        (e) => !id.includes(e),
      );
      const selectedResources = state.selectedResources.filter(
        (e) => !id.includes(e),
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
  deselectAll: (type: ElementType) => {
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
  clearListView: () => {
    set((state) => {
      return {
        ...state,
        resourceList: [],
        folderList: [],
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
});

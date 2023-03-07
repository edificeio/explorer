import { BUS } from "@shared/constants";
import { wrapTreeNode } from "@shared/utils/wrapTreeNode";
import {
  ACTION,
  RESOURCE,
  FOLDER,
  type GetResourcesResult,
  type IResource,
  type IActionResult,
  type IFolder,
  type UpdateParameters,
  odeServices,
} from "ode-ts-client";
import { type StateCreator } from "zustand";

import { type State } from ".";

export interface ResourceSlice {
  resources: IResource[];
  selectedResources: string[];
  hasMoreResources: boolean;
  openResource: (assetId: string) => Promise<IActionResult | undefined>;
  openSelectedResource: () => Promise<void>;
  printSelectedResource: () => Promise<void>;
  createResource: () => Promise<void>;
  updateResource: (params: UpdateParameters) => Promise<void>;
  isResourceSelected: (res: IResource) => boolean;
  getMoreResources: () => Promise<void>;
  // getHasResources: () => boolean;
  getSelectedIResources: () => IResource[];
  getSelectedIFolders: () => IFolder[];
}

// https://docs.pmnd.rs/zustand/guides/typescript#slices-pattern
export const createResourceSlice: StateCreator<State, [], [], ResourceSlice> = (
  set,
  get,
) => ({
  resources: [],
  selectedResources: [],
  hasMoreResources: false,
  createResource: async () => {
    try {
      const { searchParams } = get();
      await BUS.publish(searchParams.types[0], ACTION.CREATE, "");
    } catch (error) {
      // if failed push error
      console.error("explorer create failed: ", error);
      /*  addNotification(
        { type: "error", message: "explorer.create.failed" },
        toastDelay,
        set,
      ); */
    }
  },
  updateResource: async (params: UpdateParameters) => {
    const { searchParams } = get();
    const result = await odeServices
      .resource(searchParams.app, searchParams.types[0])
      .update(params);
    set((state) => {
      const resources = state.resources.map((res) => {
        if (res.assetId === params.entId) {
          return {
            ...res,
            name: params.name,
            thumbnail: result.thumbnail!,
            public: params.public,
            description: params.description,
            slug: params.slug,
          };
        } else {
          return res;
        }
      });
      return { ...state, resources };
    });
  },
  openResource: async (assetId: string) => {
    try {
      const { searchParams } = get();
      const res = await BUS.publish(searchParams.types[0], ACTION.OPEN, {
        resourceId: assetId,
      });
      return res;
    } catch (error) {
      // if failed push error
      console.error("explorer open failed: ", error);
      /* addNotification(
        { type: "error", message: "explorer.open.failed" },
        toastDelay,
        set,
      ); */
    }
  },
  openSelectedResource: async () => {
    try {
      const { searchParams, selectedResources, resources } = get();
      if (selectedResources.length > 1) {
        throw new Error("Cannot open more than 1 resource");
      }
      const item = resources.find(
        (resource: IResource) => resource.id === selectedResources[0],
      )!;
      await BUS.publish(searchParams.types[0], ACTION.OPEN, {
        resourceId: item.assetId,
      });
    } catch (error) {
      // if failed push error
      console.error("explorer open failed: ", error);
      /* addNotification(
        { type: "error", message: "explorer.open.failed" },
        toastDelay,
        set,
      ); */
    }
  },
  printSelectedResource: async () => {
    try {
      const { searchParams, selectedResources, resources } = get();
      if (selectedResources.length !== 1) {
        throw new Error("Cannot open more than 1 resource");
      }
      const item = resources.find(
        (resource: IResource) => resource.id === selectedResources[0],
      )!;
      await BUS.publish(searchParams.types[0], ACTION.PRINT, {
        resourceId: item.assetId,
      });
    } catch (error) {
      // if failed push error
      console.error("explorer print failed: ", error);
      /* addNotification(
        { type: "error", message: "explorer.print.failed" },
        toastDelay,
        set,
      ); */
    }
  },
  isResourceSelected(resource: IResource) {
    const { selectedResources } = get();
    return selectedResources.includes(resource.id);
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
      const { folders, resources, pagination } = (await BUS.publish(
        RESOURCE.FOLDER,
        ACTION.SEARCH,
        searchParams,
      )) as GetResourcesResult;

      const hasReachLimit = pagination.startIdx === pagination.maxIdx;

      set((state) => {
        return {
          ...state,
          folders,
          resources: [...state.resources, ...resources],
          treeData: wrapTreeNode(
            state.treeData,
            folders,
            searchParams.filters.folder || FOLDER.DEFAULT,
          ),
          searchParams: {
            ...state.searchParams,
            pagination,
          },
          hasMoreResources: hasReachLimit,
        };
      });
    } catch (error) {
      // if failed push error
      console.error("explorer getmore failed: ", error);
      /* addNotification(
        { type: "error", message: "explorer.getmore.failed" },
        toastDelay,
        set,
      ); */
    }
  },
  getSelectedIResources(): IResource[] {
    const { selectedResources, resources } = get();
    return resources.filter((resource: IResource) =>
      selectedResources.includes(resource.id),
    );
  },
  getSelectedIFolders(): IFolder[] {
    const { selectedFolders, folders } = get();
    return folders.filter((folder: IFolder) =>
      selectedFolders.includes(folder.id),
    );
  },
});

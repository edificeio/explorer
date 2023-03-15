import { wrapTreeNode } from "@shared/utils/wrapTreeNode";
import {
  FOLDER,
  type IResource,
  type IFolder,
  type UpdateParameters,
  odeServices,
  type ShareRight,
} from "ode-ts-client";
import { type PutShareResponse } from "ode-ts-client/dist/services/ShareService";
import { type StateCreator } from "zustand";

import { type State } from ".";

export interface ResourceSlice {
  resources: IResource[];
  selectedResources: string[];
  hasMoreResources: boolean;
  openResource: (assetId: string) => void;
  openSelectedResource: () => Promise<void>;
  printSelectedResource: () => void;
  createResource: () => Promise<void>;
  updateResource: (params: UpdateParameters) => Promise<void>;
  isResourceSelected: (res: IResource) => boolean;
  getMoreResources: () => Promise<void>;
  // getHasResources: () => boolean;
  getSelectedIResources: () => IResource[];
  getSelectedIFolders: () => IFolder[];
  shareResource: (
    entId: string,
    shares: ShareRight[],
  ) => Promise<PutShareResponse>;
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
      odeServices.resource(searchParams.app).gotoForm();
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
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const result = await odeServices
      .resource(searchParams.app, searchParams.types[0])
      .update(params);
    set((state) => {
      const resources = state.resources.map((res) => {
        if (res.assetId === params.entId) {
          const {
            name,
            thumbnail,
            public: pub,
            description,
            slug,
            entId,
            ...others
          } = params;
          return {
            ...res,
            ...others, // add any custom field
            name,
            thumbnail: thumbnail! as string,
            public: pub,
            description,
            slug,
          };
        } else {
          return res;
        }
      });
      return { ...state, resources };
    });
  },
  openResource: (assetId: string) => {
    try {
      const { searchParams } = get();
      odeServices.resource(searchParams.app).gotoView(assetId);
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
      odeServices.resource(searchParams.app).gotoView(item.assetId);
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
  printSelectedResource: () => {
    try {
      const { searchParams, selectedResources, resources } = get();
      if (selectedResources.length !== 1) {
        throw new Error("Cannot open more than 1 resource");
      }
      const item = resources.find(
        (resource: IResource) => resource.id === selectedResources[0],
      )!;
      odeServices.resource(searchParams.app).gotoPrint(item.assetId);
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
      const { searchParams: searchParamsOrig, getCurrentFolderId } = get();
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
      const trashed = getCurrentFolderId() === FOLDER.BIN;
      const { folders, resources, pagination } = await odeServices
        .resource(searchParams.app)
        .searchContext({ ...searchParams, trashed });
      const currentMaxIdx = pagination.startIdx + pagination.pageSize - 1;
      const hasMoreResources = currentMaxIdx < (pagination.maxIdx || 0);

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
          hasMoreResources,
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
  shareResource: async (
    entId: string,
    shares: ShareRight[],
  ): Promise<PutShareResponse> => {
    const { searchParams } = get();
    const result = await odeServices
      .share()
      .saveRights(searchParams.app, entId, shares);
    set((state) => {
      const resources = state.resources.map((res) => {
        if (res.assetId === entId) {
          return {
            ...res,
            shared: shares.length > 0,
          };
        } else {
          return res;
        }
      });
      return { ...state, resources };
    });
    return result;
  },
});

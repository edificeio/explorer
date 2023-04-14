import {
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
  openResource: (resource: IResource) => void;
  openSelectedResource: () => Promise<void>;
  printSelectedResource: () => void;
  createResource: () => Promise<void>;
  updateResource: (params: UpdateParameters) => Promise<void>;
  isResourceSelected: (res: IResource) => boolean;
  getSelectedIResources: () => IResource[];
  getSelectedIFolders: () => IFolder[];
  shareResource: (
    entId: string,
    shares: ShareRight[],
  ) => Promise<PutShareResponse>;
  setResources: (data: any) => void;
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
      const { searchParams, getCurrentFolderId } = get();
      const folderId = parseInt(getCurrentFolderId()!);
      const safeFolderId = isNaN(folderId) ? undefined : folderId;
      odeServices
        .resource(searchParams.app)
        .gotoForm(safeFolderId as string | undefined);
    } catch (error) {
      // if failed push error
      console.error("explorer create failed: ", error);
    }
  },
  setResources: (data: any) =>
    set((state) => ({
      ...state,
      resources: [...state.resources, ...data],
    })),
  updateResource: async (params: UpdateParameters) => {
    const { searchParams } = get();
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const result = await odeServices.resource(searchParams.app).update(params);
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
  openResource: (resource: IResource) => {
    if (resource.trashed) return;
    try {
      const { searchParams } = get();
      odeServices.resource(searchParams.app).gotoView(resource.assetId);
    } catch (error) {
      // if failed push error
      console.error("explorer open failed: ", error);
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

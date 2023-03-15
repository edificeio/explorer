import { type TreeNode } from "@ode-react-ui/advanced";
import { deleteNode } from "@shared/utils/deleteNode";
import {
  type IResource,
  type TrashParameters,
  FOLDER,
  type IFolder,
  odeServices,
} from "ode-ts-client";
import toast from "react-hot-toast";
import { type StateCreator } from "zustand";

import { type State } from ".";

export interface TrashSlice {
  trashOrRestore: (props: {
    selectedResources: string[];
    selectedFolders: string[];
    operation: "trash" | "restore";
  }) => Promise<void>;
  trashSelection: () => Promise<void>;
  restoreSelection: () => Promise<void>;
  getIsTrashSelected: () => boolean;
  gotoTrash: () => void;
}

// https://docs.pmnd.rs/zustand/guides/typescript#slices-pattern
export const createTrashSlice: StateCreator<State, [], [], TrashSlice> = (
  set,
  get,
) => ({
  trashOrRestore: async ({
    selectedFolders,
    selectedResources,
    operation,
  }: {
    selectedFolders: string[];
    selectedResources: string[];
    operation: "trash" | "restore";
  }) => {
    try {
      const { searchParams } = get();
      const parameters: Omit<TrashParameters, "trash"> = {
        application: searchParams.app,
        resourceType: searchParams.types[0],
        resourceIds: selectedResources,
        folderIds: selectedFolders,
      };
      if (operation === "trash") {
        await odeServices.resource(searchParams.app).trashAll(parameters);
      } else {
        await odeServices.resource(searchParams.app).restoreAll(parameters);
      }
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
      console.error("explorer trash failed: ", error);
      toast.error("explorer.restore.failed");
      /* addNotification(
        {
          type: "error",
          message: trash ? "explorer.trash.failed" : "explorer.restore.failed",
        },
        toastDelay,
        set,
      ); */
    }
  },
  trashSelection: async () => {
    const { selectedFolders, selectedResources, trashOrRestore } = get();
    trashOrRestore({ selectedFolders, selectedResources, operation: "trash" });
  },
  restoreSelection: async () => {
    const { selectedFolders, selectedResources, trashOrRestore } = get();
    trashOrRestore({
      selectedFolders,
      selectedResources,
      operation: "restore",
    });
  },
  getIsTrashSelected() {
    const { getCurrentFolderId } = get();
    return (getCurrentFolderId() as string) === FOLDER.BIN;
  },
  gotoTrash: () => {
    const { openFolder } = get();
    openFolder(FOLDER.BIN);
  },
});

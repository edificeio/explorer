import { type TreeNode } from "@ode-react-ui/advanced";
import { BUS } from "@shared/constants";
import { deleteNode } from "@shared/utils/deleteNode";
import {
  ACTION,
  type IResource,
  RESOURCE,
  type TrashParameters,
  FOLDER,
  type IFolder,
} from "ode-ts-client";
import toast from "react-hot-toast";
import { type StateCreator } from "zustand";

import { type State } from ".";

export interface TrashSlice {
  trash: (props: {
    selectedResources: string[];
    selectedFolders: string[];
    trash: boolean;
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
  trash: async ({
    selectedFolders,
    selectedResources,
    trash,
  }: {
    selectedFolders: string[];
    selectedResources: string[];
    trash: boolean;
  }) => {
    try {
      const { searchParams } = get();
      const parameters: TrashParameters = {
        trash,
        application: searchParams.app,
        resourceType: searchParams.types[0],
        resourceIds: selectedResources,
        folderIds: selectedFolders,
      };
      await BUS.publish(RESOURCE.FOLDER, ACTION.TRASH, parameters);
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
    const { selectedFolders, selectedResources, trash } = get();
    trash({ selectedFolders, selectedResources, trash: true });
  },
  restoreSelection: async () => {
    const { selectedFolders, selectedResources, trash } = get();
    trash({ selectedFolders, selectedResources, trash: false });
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

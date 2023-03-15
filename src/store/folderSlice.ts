/* eslint-disable @typescript-eslint/no-misused-promises */
import { type TreeNode } from "@ode-react-ui/advanced";
import { addNode } from "@shared/utils/addNode";
import { arrayUnique } from "@shared/utils/arrayUnique";
import { getAncestors } from "@shared/utils/getAncestors";
import { updateNode } from "@shared/utils/updateNode";
import { wrapTreeNode } from "@shared/utils/wrapTreeNode";
import {
  type CreateFolderParameters,
  type UpdateFolderParameters,
  type IFolder,
  FOLDER,
  type ISearchParameters,
  odeServices,
} from "ode-ts-client";
import { toast } from "react-hot-toast";
import { type StateCreator } from "zustand";

import { type State } from ".";

export interface FolderSlice {
  folders: IFolder[];
  selectedFolders: string[];
  isFolderSelected: (folder: IFolder) => boolean;
  getSelectedFolders: () => IFolder[];
  openFolder: (id: string) => Promise<void>;
  createFolder: (name: string, parentId: string) => Promise<IFolder>;
  updateFolder: (props: {
    id: string;
    name: string;
    parentId: string;
  }) => Promise<void>;
  loadSubfolders: (folderId: string) => Promise<void>;
  getCurrentFolderId: () => string | undefined;
  gotoPreviousFolder: () => void;
}

// https://docs.pmnd.rs/zustand/guides/typescript#slices-pattern
export const createFolderSlice: StateCreator<State, [], [], FolderSlice> = (
  set,
  get,
) => ({
  folders: [],
  selectedFolders: [],
  isFolderSelected(folder: IFolder) {
    const { selectedFolders } = get();
    return selectedFolders.includes(folder.id);
  },
  getSelectedFolders(): IFolder[] {
    const { selectedFolders, folders } = get();
    return folders.filter((folder: IFolder) =>
      selectedFolders.includes(folder.id),
    );
  },
  getCurrentFolderId(): string | undefined {
    const { searchParams } = get();
    return searchParams.filters.folder;
  },
  createFolder: async (name: string, parentId: string) => {
    try {
      const { searchParams } = get();
      const parameters: CreateFolderParameters = {
        name,
        parentId,
        app: searchParams.app,
        type: searchParams.types[0],
      };
      const folder = await odeServices
        .resource(searchParams.app)
        .createFolder(parameters);

      set((state: { treeData: TreeNode; folders: IFolder[] }) => {
        // add folder in tree
        const treeData = addNode(state.treeData, {
          parentId,
          newFolder: folder,
        });
        // add folder in list
        const folders = [...state.folders, folder];
        return { ...state, treeData, folders };
      });

      toast.success("empty.workspace.subfolder.title");
      return folder;
    } catch (error) {
      // if failed push error
      console.error("explorer create failed: ", error);
      toast.error("explorer.create.failed");
      throw error;
    }
  },
  updateFolder: async (folder: {
    id: string;
    name: string;
    parentId: string;
  }) => {
    try {
      const { searchParams } = get();
      const folderId = folder.id;
      const parameters: UpdateFolderParameters = {
        folderId,
        name: folder.name,
        parentId: folder.parentId,
        app: searchParams.app,
        type: searchParams.types[0],
      };
      const newFolder = await odeServices
        .resource(searchParams.app)
        .updateFolder(parameters);
      set((state: { treeData: TreeNode; folders: IFolder[] }) => {
        // replace folder in tree
        const updatedTreeData = updateNode(state.treeData, {
          folderId,
          newFolder,
        });
        // replace folder in list
        const updatedFolders: IFolder[] = state.folders.map(
          (folder: IFolder) => {
            if (folder.id === folderId) {
              return { ...newFolder, rights: folder.rights };
            } else {
              return folder;
            }
          },
        );
        return {
          ...state,
          treeData: updatedTreeData,
          folders: updatedFolders,
        };
      });
    } catch (error) {
      // if failed push error
      console.error("explorer update failed: ", error);
      /* addNotification(
        { type: "error", message: "explorer.update.failed" },
        toastDelay,
        set,
      ); */
    }
  },
  openFolder: async (folderId: string) => {
    const { searchParams, treeData, reloadListView } = get();
    const previousId = searchParams.filters.folder as string;
    const ancestors = getAncestors(folderId, treeData);
    const selectedNodeIds = arrayUnique([...ancestors, folderId]);
    if (previousId === folderId) return;
    // set selected nodes and current folder and previous item and filter unique
    set((state) => {
      return {
        ...state,
        // reset selection when changing folder
        selectedFolders: [],
        selectedResources: [],
        selectedNodeIds,
        searchParams: {
          ...searchParams,
          filters: {
            ...searchParams.filters,
            folder: folderId,
          },
        },
      };
    });
    // reset list view
    reloadListView();
  },
  loadSubfolders: async (folderId: string) => {
    try {
      const { searchParams } = get();
      const newSerchParams: ISearchParameters = {
        ...searchParams,
        filters: {
          ...searchParams.filters,
          folder: folderId,
        },
      };
      // fetch subfolders
      const { folders } = await odeServices
        .resource(searchParams.app)
        .searchContext(newSerchParams);
      set((state: { treeData: TreeNode }) => {
        return {
          ...state,
          treeData: wrapTreeNode(
            state.treeData,
            folders,
            folderId || FOLDER.DEFAULT,
          ),
        };
      });
    } catch (error) {
      // if failed push error
      console.error("explorer reload failed: ", error);
      /* addNotification(
        { type: "error", message: "explorer.reload.failed" },
        toastDelay,
        set,
      ); */
    }
  },
  gotoPreviousFolder: async () => {
    const { getPreviousFolder, openFolder } = get();
    await openFolder(getPreviousFolder()?.id || FOLDER.DEFAULT);
  },
});

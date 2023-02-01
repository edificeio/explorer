import { addNode } from "@shared/utils/addNode";
import { arrayUnique } from "@shared/utils/arrayUnique";
import { getAncestors } from "@shared/utils/getAncestors";
import { hasChildren } from "@shared/utils/hasChildren";
import {
  ACTION,
  CreateFolderParameters,
  CreateFolderResult,
  FOLDER,
  GetResourcesResult,
  IBus,
  ISearchParameters,
  RESOURCE,
} from "ode-ts-client";

import { GetExplorerStateFunction, SetExplorerStateFunction } from "./types";
import { addNotification, wrapTreeNode } from "./utils";

export const withTreeView = ({
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
  createFolder: async (name: string, parentId: string) => {
    try {
      const { searchParams } = get();
      const parameters: CreateFolderParameters = {
        name,
        parentId,
        app: searchParams.app,
        type: searchParams.types[0],
      };
      const folder = (await bus.publish(
        RESOURCE.FOLDER,
        ACTION.CREATE,
        parameters,
      )) as CreateFolderResult;
      set(
        ({ treeData: treeDataOrig, folderList: folderListOrig, ...state }) => {
          // add folder in tree
          const treeData = addNode(treeDataOrig, {
            parentId,
            newFolder: folder,
          });
          // add folder in list
          const folderList = [...folderListOrig, folder];
          return { ...state, treeData, folderList };
        },
      );
      return folder;
    } catch (e) {
      // if failed push error
      console.error("explorer create failed: ", e);
      addNotification(
        { type: "error", message: "explorer.create.failed" },
        toastDelay,
        set,
      );
      throw e;
    }
  },
  foldTreeItem: (folderId: string) => {
    set((state) => {
      return { ...state, treeviewStatus: "fold" };
    });
  },
  unfoldTreeItem: (folderId: string) => {
    const { treeData, loadSubfolders } = get();
    set((state) => {
      return { ...state, treeviewStatus: "unfold" };
    });
    // load subfolders if needed
    if (!hasChildren(folderId, treeData)) {
      loadSubfolders(folderId);
    }
  },
  selectTreeItem: (folderId: string) => {
    // select and open folder
    const { openFolder } = get();
    set((state) => {
      return { ...state, treeviewStatus: "select" };
    });
    openFolder(folderId);
  },
  gotoTrash: () => {
    const { openFolder } = get();
    openFolder(FOLDER.BIN);
  },
  gotoPreviousFolder: async () => {
    const { getPreviousFolder, openFolder } = get();
    await openFolder(getPreviousFolder()?.id || FOLDER.DEFAULT);
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
      const { folders } = (await bus.publish(
        RESOURCE.FOLDER,
        ACTION.SEARCH,
        newSerchParams,
      )) as GetResourcesResult;
      set((state) => {
        return {
          ...state,
          treeData: wrapTreeNode(
            state.treeData,
            folders,
            folderId || FOLDER.DEFAULT,
          ),
        };
      });
    } catch (e) {
      // if failed push error
      console.error("explorer reload failed: ", e);
      addNotification(
        { type: "error", message: "explorer.reload.failed" },
        toastDelay,
        set,
      );
    }
  },
});

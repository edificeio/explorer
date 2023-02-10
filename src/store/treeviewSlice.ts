import { type TreeNode } from "@ode-react-ui/advanced";
import { findNodeById } from "@shared/utils/findNodeById";
import { hasChildren } from "@shared/utils/hasChildren";
import { FOLDER } from "ode-ts-client";
import { type StateCreator } from "zustand";

import { type State } from ".";

export interface TreeviewSlice {
  selectedNodeIds: string[];
  status: "fold" | "unfold" | "select" | undefined;
  treeData: TreeNode;
  foldTreeItem: (folderId: string) => void;
  getHasNoSelectedNodes: () => boolean;
  getHasSelectedRoot: () => boolean;
  getPreviousFolder: () => TreeNode | undefined;
  selectTreeItem: (folderId: string) => void;
  unfoldTreeItem: (folderId: string) => void;
}

// https://docs.pmnd.rs/zustand/guides/typescript#slices-pattern
export const createTreeviewSlice: StateCreator<State, [], [], TreeviewSlice> = (
  set,
  get,
) => ({
  selectedNodeIds: [],
  status: undefined,
  treeData: {
    id: FOLDER.DEFAULT,
    name: "explorer.filters.mine",
    section: true,
    children: [],
  },
  getPreviousFolder() {
    const { selectedNodeIds, treeData } = get();
    if (selectedNodeIds.length < 2) {
      return undefined;
    }
    const previousFolder = findNodeById(
      selectedNodeIds[selectedNodeIds.length - 2],
      treeData,
    );
    return previousFolder && { ...previousFolder };
  },
  foldTreeItem: () => {
    set((state: any) => {
      return { ...state, status: "fold" };
    });
  },
  unfoldTreeItem: (folderId: string) => {
    const { treeData, loadSubfolders } = get();
    set((state: any) => {
      return { ...state, status: "unfold" };
    });
    // load subfolders if needed
    if (!hasChildren(folderId, treeData)) {
      loadSubfolders(folderId);
    }
  },
  selectTreeItem: (folderId: string) => {
    // select and open folder
    const { openFolder } = get();
    set((state: any) => {
      return { ...state, status: "select" };
    });
    openFolder(folderId);
  },
  getHasNoSelectedNodes() {
    const { selectedNodeIds } = get();
    const isRootNode =
      selectedNodeIds[0] === FOLDER.DEFAULT ||
      selectedNodeIds[0] === FOLDER.BIN;
    const hasOnlyRoot = selectedNodeIds.length === 1 && isRootNode;
    const hasNoSelectedNodes = selectedNodeIds?.length === 0 || hasOnlyRoot;
    return hasNoSelectedNodes;
  },
  getHasSelectedRoot() {
    const { selectedNodeIds } = get();
    return selectedNodeIds?.length === 1;
  },
});

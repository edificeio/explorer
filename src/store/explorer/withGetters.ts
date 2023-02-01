import { findNodeById } from "@shared/utils/findNodeById";
import { FOLDER, IFolder, IResource } from "ode-ts-client";

import { GetExplorerStateFunction } from "./types";

export const withGetters = ({ get }: { get: GetExplorerStateFunction }) => ({
  getSelectedIResources(): IResource[] {
    const { selectedResources, resourceList } = get();
    return resourceList.filter((e) => selectedResources.includes(e.id));
  },
  getSelectedIFolders(): IFolder[] {
    const { selectedFolders, folderList } = get();
    return folderList.filter((e) => selectedFolders.includes(e.id));
  },
  getCurrentFolderId(): string | undefined {
    const { searchParams } = get();
    return searchParams.filters.folder;
  },
  getIsTrashSelected() {
    const { getCurrentFolderId } = get();
    return (getCurrentFolderId() as string) === FOLDER.BIN;
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
  getHasResources() {
    const { resourceList } = get();
    const hasResources = resourceList.length;
    return hasResources > 0;
  },
  getHasResourcesOrFolders() {
    const { resourceList, folderList } = get();
    const hasResourcesOrFolders = resourceList.length + folderList.length;
    return hasResourcesOrFolders > 0;
  },
});

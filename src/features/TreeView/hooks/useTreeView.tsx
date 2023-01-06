import { useCallback } from "react";

import { TreeNode } from "@features/Explorer/types";
import { IExplorerContext, RESOURCE } from "ode-ts-client";

export default function useTreeView(
  context: IExplorerContext,
  treeData: TreeNode,
) {
  const handleTreeItemSelect = useCallback((folderId: string) => {
    console.log("tree item selected = ", folderId);
    context.getSearchParameters().filters.folder = folderId;
    context.getSearchParameters().types = ["blog"];
    context.getResources();
  }, []);

  const handleTreeItemFold = useCallback((folderId: any) => {
    console.log("tree item folded = ", folderId);
  }, []);

  const hasChildren = useCallback(
    (folderId: string, data: TreeNode): boolean => {
      if (data.id === folderId && data.children) {
        return data.children.length > 0;
      }
      if (data.children) {
        return data.children.some((child: any) => hasChildren(data.id, child));
      }
      return false;
    },
    [],
  );

  const handleTreeItemUnfold = useCallback((folderId: any) => {
    console.log("tree item unfolded = ", folderId);

    if (!hasChildren(folderId, treeData)) {
      context.getSearchParameters().filters.folder = folderId;
      context.getSearchParameters().types = [RESOURCE.FOLDER];
      context.getResources();
    }
  }, []);

  return {
    handleTreeItemSelect,
    handleTreeItemFold,
    handleTreeItemUnfold,
  };
}

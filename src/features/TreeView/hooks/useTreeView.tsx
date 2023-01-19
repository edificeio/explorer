import { useCallback, useEffect, useState } from "react";

import { useExplorerContext } from "@contexts/ExplorerContext/ExplorerContext";
import { TreeNode } from "@features/Explorer/types";
import { ID, RESOURCE } from "ode-ts-client";

export default function useTreeView() {
  const {
    dispatch,
    context,
    state: { treeData },
  } = useExplorerContext();

  /* const [folderId, setFolderId] = useState<ID>(
    context.getSearchParameters().filters.folder,
  );
  const lastValue = usePrevious(folderId); */

  const [previousId, setPreviousId] = useState<ID>();

  const handleTreeItemSelect = useCallback(
    (folderId: string) => {
      const previous = context.getSearchParameters().filters.folder;
      setPreviousId(previous);

      console.log("previous", context.getSearchParameters().filters.folder);
      console.log("folderId", folderId);

      dispatch({ type: "CLEAR_RESOURCES" });

      context.getSearchParameters().filters.folder = folderId;
      context.getSearchParameters().types = ["blog"];
      context.getSearchParameters().pagination.startIdx = 0;
      context.getResources();
    },
    [previousId],
  );

  useEffect(() => {
    console.log("previousId", previousId);
  }, [previousId]);

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
      context.getSearchParameters().pagination.startIdx = 0;
      context.getResources();
    }
  }, []);

  return {
    previousId,
    handleTreeItemSelect,
    handleTreeItemFold,
    handleTreeItemUnfold,
  };
}

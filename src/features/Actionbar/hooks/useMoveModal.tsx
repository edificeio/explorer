import { useState } from "react";

import { useExplorerContext } from "@contexts/index";
import useTreeView from "@features/TreeView/hooks/useTreeView";

interface MoveModalArg {
  onSuccess?: () => void;
}

export default function useMoveModal({ onSuccess }: MoveModalArg) {
  const [selectedFolder, setSelectedFolder] = useState<string | undefined>();
  const { contextRef, selectedResources, selectedFolders, state } =
    useExplorerContext();
  const { treeData } = state;
  /* feature treeview @hook */
  const { handleTreeItemFold, handleTreeItemUnfold } = useTreeView();

  async function onMove() {
    try {
      if (!selectedFolder) {
        throw new Error("explorer.move.selection.empty");
      }
      const resourceIds = selectedResources.map((e) => e.id);
      const folderIds = selectedFolders.map((e) => e.id);
      await contextRef.current.move(selectedFolder, resourceIds, folderIds);
      onSuccess?.();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  return {
    handleTreeItemSelect: (folderId: string) => {
      setSelectedFolder(folderId);
    },
    handleTreeItemFold,
    handleTreeItemUnfold,
    onMove: () => {
      onMove();
    },
    treeData,
  };
}

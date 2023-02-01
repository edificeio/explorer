import { useState } from "react";

import { useExplorerContext } from "@contexts/index";

interface MoveModalArg {
  onSuccess?: () => void;
}

export default function useMoveModal({ onSuccess }: MoveModalArg) {
  const [selectedFolder, setSelectedFolder] = useState<string | undefined>();
  const {
    treeData,
    getSelectedIFolders,
    moveSelectedTo,
    foldTreeItem,
    unfoldTreeItem,
  } = useExplorerContext();
  /* feature treeview @hook */

  async function onMove() {
    try {
      if (!selectedFolder) {
        throw new Error("explorer.move.selection.empty");
      }
      await moveSelectedTo(selectedFolder);
      onSuccess?.();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  const canMove = (destination: string) => {
    for (const selectedFolder of getSelectedIFolders()) {
      if (
        destination === selectedFolder.id ||
        destination === selectedFolder.parentId
      ) {
        return false;
      }
    }
    return true;
  };

  return {
    disableSubmit: !selectedFolder,
    handleTreeItemSelect: (folderId: string) => {
      if (canMove(folderId)) {
        setSelectedFolder(folderId);
      } else {
        setSelectedFolder(undefined);
      }
    },
    handleTreeItemFold: foldTreeItem,
    handleTreeItemUnfold: unfoldTreeItem,
    onMove: () => {
      onMove();
    },
    treeData,
  };
}

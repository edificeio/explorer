import { useState } from "react";

import useExplorerStore from "@store/index";

interface ModalProps {
  onSuccess?: () => void;
}

export default function useMoveModal({ onSuccess }: ModalProps) {
  const [selectedFolder, setSelectedFolder] = useState<string | undefined>();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const {
    treeData,
    foldTreeItem,
    unfoldTreeItem,
    moveSelectedTo,
    getSelectedFolders,
  } = useExplorerStore();

  async function onMove() {
    try {
      if (!selectedFolder) throw new Error("explorer.move.selection.empty");

      await moveSelectedTo(selectedFolder);
      onSuccess?.();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  const canMove = (destination: string) => {
    for (const selectedFolder of getSelectedFolders()) {
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

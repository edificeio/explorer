import { useCallback, useState } from "react";

import { useExplorerContext } from "@contexts/ExplorerContext/ExplorerContext";
import { TreeNode } from "@features/Explorer/types";
import { useOdeStore, useSetPreviousFolder } from "@store/useOdeStore";
import { FOLDER, IFolder, RESOURCE } from "ode-ts-client";

export default function useTreeView() {
  const [isOpenedModal, setOpenedModal] = useState<boolean>(false);
  const previousFolder = useOdeStore((state) => state.previousFolder);
  const setPreviousFolder = useSetPreviousFolder();
  const clearPreviousFolder = useOdeStore((state) => state.clearPreviousFolder);
  const {
    dispatch,
    refreshFolder,
    contextRef,
    state: { treeData, folders },
  } = useExplorerContext();

  function getResources(types?: any) {
    contextRef.current.getSearchParameters().types = types;
    contextRef.current.getSearchParameters().pagination.startIdx = 0;
    contextRef.current.getResources();
  }

  const handleNavigationBack = () => {
    dispatch({ type: "CLEAR_RESOURCES" });

    const lastElement = previousFolder[previousFolder.length - 1];

    contextRef.current.getSearchParameters().filters.folder = lastElement.id;

    getResources(["blog"]);

    clearPreviousFolder();
  };

  const handleNavigationFolder = (folderId: string) => {
    dispatch({ type: "CLEAR_RESOURCES" });

    const findItem = folders.find((item) => item.id === folderId);
    const previousId = contextRef.current.getSearchParameters().filters
      .folder as string;

    if (previousId === folderId) return;

    contextRef.current.getSearchParameters().filters.folder = folderId;

    getResources(["blog"]);

    setPreviousFolder(previousId, findItem?.name as string);
  };

  const handleTreeItemSelect = useCallback((folderId: string) => {
    dispatch({ type: "CLEAR_RESOURCES" });

    contextRef.current.getSearchParameters().filters.folder = folderId;
    contextRef.current.getSearchParameters().pagination.startIdx = 0;

    getResources(["blog"]);
  }, []);

  const handleTreeItemFold = useCallback((folderId: any) => {
    // console.log("tree item folded = ", folderId);
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
    // console.log("tree item unfolded = ", folderId);

    if (!hasChildren(folderId, treeData)) {
      contextRef.current.getSearchParameters().filters.folder = folderId;

      getResources([RESOURCE.FOLDER]);
    }
  }, []);
  const trashSelected =
    contextRef.current.getSearchParameters().filters.folder === FOLDER.BIN;

  const onClose = () => {
    setOpenedModal(false);
  };
  const onOpen = () => {
    setOpenedModal(true);
  };
  const onCreateSuccess = (folder: IFolder) => {
    setOpenedModal(false);
    refreshFolder({ addFolder: folder });
  };
  return {
    treeData,
    trashId: FOLDER.BIN,
    trashSelected,
    handleNavigationBack,
    handleNavigationFolder,
    handleTreeItemSelect,
    handleTreeItemFold,
    handleTreeItemUnfold,
    isOpenedModal,
    onOpen,
    onClose,
    onCreateSuccess,
  };
}

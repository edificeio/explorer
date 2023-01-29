import { useCallback, useState } from "react";

import { useExplorerContext } from "@contexts/ExplorerContext/ExplorerContext";
import { findNodeById } from "@shared/utils/findNodeById";
import { hasChildren } from "@shared/utils/hasChildren";
import { useOdeStore } from "@store/useOdeStore";
import { FOLDER, IFolder, RESOURCE, ResourceType } from "ode-ts-client";

export default function useTreeView() {
  const [isOpenedModal, setOpenedModal] = useState<boolean>(false);

  const selectedNodesIds = useOdeStore((state) => state.selectedNodesIds);
  const setSelectedNodesIds = useOdeStore((state) => state.setSelectedNodesIds);

  const {
    dispatch,
    refreshFolder,
    contextRef,
    state: { treeData },
  } = useExplorerContext();

  const trashSelected =
    contextRef.current.getSearchParameters().filters.folder === FOLDER.BIN;

  /**
   *
   * @param id FolderId
   * @param types ResourceType[]
   * return a new context and get resources
   */
  function getResources(id: string, types: ResourceType[]) {
    contextRef.current.getSearchParameters().filters.folder = id;
    contextRef.current.getSearchParameters().types = types;
    contextRef.current.getSearchParameters().pagination.startIdx = 0;
    contextRef.current.getResources();
  }

  /**
   * Select folder and get new sub folders and resources
   */
  const handleNavigationFolder = (folderId: string) => {
    const previousId = contextRef.current.getSearchParameters().filters
      .folder as string;

    const findItem = findNodeById(folderId, treeData);

    if (previousId === folderId) return;

    dispatch({ type: "CLEAR_RESOURCES" });

    let nodes: string[] = [];

    if (findItem?.folder?.ancestors) {
      nodes = findItem?.folder.ancestors;
      nodes = [...nodes, folderId];
    } else {
      nodes = ["default"];
    }

    if (!hasChildren(folderId, treeData)) {
      getResources(folderId, [RESOURCE.FOLDER]);
    }

    getResources(folderId, ["blog"]);
    setSelectedNodesIds(nodes);
  };

  /**
   * Redirect user to previous selected folder
   */
  const handleNavigationBack = useCallback(
    (folderId: string) => {
      handleNavigationFolder(folderId);
    },
    [selectedNodesIds],
  );

  /**
   * Select folder from the TreeView Component
   * Uses handleNavigationFolder
   */
  const handleTreeItemSelect = useCallback((folderId: string) => {
    dispatch({ type: "GET_TREEVIEW_ACTION", payload: "select" });

    handleNavigationFolder(folderId);
  }, []);

  /**
   * Action when TreeItem is folded
   */
  const handleTreeItemFold = useCallback(() => {
    dispatch({ type: "GET_TREEVIEW_ACTION", payload: "fold" });
  }, []);

  /**
   * Action when TreeItem is unfolded
   * Dispatch the action and get new folders only for TreeData
   */
  const handleTreeItemUnfold = useCallback((folderId: any) => {
    dispatch({ type: "GET_TREEVIEW_ACTION", payload: "unfold" });

    if (!hasChildren(folderId, treeData)) {
      getResources(folderId, [RESOURCE.FOLDER]);
    }
  }, []);

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

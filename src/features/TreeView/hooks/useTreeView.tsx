import { useCallback, useState } from "react";

import { useExplorerContext } from "@contexts/ExplorerContext/ExplorerContext";
import { TreeNode } from "@ode-react-ui/core";
import { findNodeById } from "@shared/utils/findNodeById";
import { hasChildren } from "@shared/utils/hasChildren";
import { useOdeStore } from "@store/useOdeStore";
import { FOLDER, ID, IFolder, RESOURCE, ResourceType } from "ode-ts-client";

type NodeFolder = TreeNode | undefined;
type ModalState = boolean;
type FolderID = ID;

interface Nodes {
  folderId: FolderID;
  findItem: NodeFolder;
}

interface Resources {
  folderId: string;
  types: ResourceType[];
}

export default function useTreeView() {
  const [isOpenedModal, setOpenedModal] = useState<ModalState>(false);

  const selectedNodesIds = useOdeStore((state) => state.selectedNodesIds);
  const setSelectedNodesIds = useOdeStore((state) => state.setSelectedNodesIds);

  const {
    dispatch,
    refreshFolder,
    contextRef,
    trashSelected,
    state: { treeData },
  } = useExplorerContext();

  function getNodes({ folderId, findItem }: Nodes): string[] {
    let nodes: string[] = [];

    if (findItem?.folder?.ancestors) {
      nodes = findItem?.folder.ancestors;
      nodes = [...nodes, folderId];
    } else {
      nodes = ["default"];
    }

    return nodes;
  }

  /**
   *
   * @param id FolderId
   * @param types ResourceType[]
   * return a new context and get resources
   */
  function getResources({ folderId, types }: Resources) {
    contextRef.current.getSearchParameters().filters.folder = folderId;
    contextRef.current.getSearchParameters().types = types;
    contextRef.current.getSearchParameters().pagination.startIdx = 0;
    contextRef.current.getResources();
  }

  /**
   * Select folder and get new sub folders and resources
   */
  const handleTreeByFolders = (folderId: FolderID) => {
    const findItem: NodeFolder = findNodeById(folderId, treeData);
    const nodes = getNodes({ folderId, findItem });

    dispatch({ type: "CLEAR_RESOURCES" });
    dispatch({ type: "GET_TREEVIEW_ACTION", payload: "select" });

    if (!hasChildren(folderId, treeData)) {
      getResources({ folderId, types: [RESOURCE.FOLDER] });
    }

    getResources({ folderId, types: ["blog"] });
    setSelectedNodesIds(nodes);
  };

  /**
   * Redirect user to previous selected folder
   */
  const handleTreeItemPrevious = useCallback(
    (folderId: FolderID) => {
      handleTreeByFolders(folderId);
    },
    [selectedNodesIds],
  );

  const handleTreeItemTrash = (folderId: FolderID) => {
    dispatch({ type: "CLEAR_RESOURCES" });
    setSelectedNodesIds([]);
    contextRef.current.getSearchParameters().filters.folder = folderId;
    contextRef.current.getSearchParameters().pagination.startIdx = 0;
    contextRef.current.getResources();
  };

  /**
   * Select folder from the TreeView Component
   * Uses handleNavigationFolder
   */
  const handleTreeItemSelect = useCallback((folderId: FolderID) => {
    dispatch({ type: "GET_TREEVIEW_ACTION", payload: "select" });

    handleTreeByFolders(folderId);
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
  const handleTreeItemUnfold = useCallback((folderId: FolderID) => {
    dispatch({ type: "GET_TREEVIEW_ACTION", payload: "unfold" });

    if (!hasChildren(folderId, treeData)) {
      getResources({ folderId, types: [RESOURCE.FOLDER] });
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
    isOpenedModal,
    handleTreeItemPrevious,
    handleTreeByFolders,
    handleTreeItemSelect,
    handleTreeItemTrash,
    handleTreeItemFold,
    handleTreeItemUnfold,
    onOpen,
    onClose,
    onCreateSuccess,
  };
}

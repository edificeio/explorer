import { useState, useEffect } from "react";

import useExplorerStore from "@store/index";
import { type IAction, ACTION } from "ode-ts-client";

type ModalName =
  | "move"
  | "delete"
  | "publish"
  | "edit_folder"
  | "edit_resource"
  | "void";

export default function useActionBar() {
  const [isActionBarOpen, setIsActionBarOpen] = useState<boolean>(false);
  const [openedModalName, setOpenedModalName] = useState<ModalName>("void");

  const {
    actions,
    getIsTrashSelected,
    getCurrentFolderId,
    openSelectedResource,
    printSelectedResource,
    createResource,
    deselectAll,
    trashSelection,
    getSelectedIResources,
    selectedResources,
    selectedFolders,
  } = useExplorerStore();

  useEffect(() => {
    if (selectedResources.length === 0 && selectedFolders.length === 0) {
      setIsActionBarOpen(false);
      return;
    }
    setIsActionBarOpen(true);
  }, [selectedResources, selectedFolders]);

  function handleClick(action: IAction) {
    switch (action.id) {
      case ACTION.OPEN:
        return openSelectedResource();
      case ACTION.CREATE:
        return createResource();
      case ACTION.MOVE:
        return setOpenedModalName("move");
      case ACTION.PRINT:
        return printSelectedResource();
      case ACTION.DELETE:
        return setOpenedModalName("delete");
      case ACTION.RESTORE:
        return onRestore();
      case ACTION.PUBLISH:
        return setOpenedModalName("publish");
      // TODO fix in ode-ts
      case ACTION.UPD_PROPS:
      case "edit" as any:
        return onEdit();
      // case ACTION.SHARE:
      //   return explorer.onShare();
      // case ACTION.MANAGE:
      //   return explorer.onManage();
      default:
        throw Error(`Unknown action: ${action.id}`);
    }
  }

  /**
   * Visibility rules for the action buttons.
   * @param action action to check
   * @returns true if the action button must be visible
   */
  function isActivable(action: IAction): boolean {
    const onlyOneItemSelected =
      selectedResources.length === 1 || selectedFolders.length === 1;
    switch (action.id) {
      case ACTION.OPEN:
        return onlyOneItemSelected;
      case ACTION.MANAGE:
        return onlyOneItemSelected;
      case ACTION.PUBLISH:
        return onlyOneItemSelected;
      case ACTION.UPD_PROPS:
        return onlyOneItemSelected;
      case "edit" as any:
        return onlyOneItemSelected;
      default:
        return true;
    }
  }

  async function onRestore() {
    try {
      if (getIsTrashSelected()) {
        await trashSelection();
      } else {
        throw new Error("Cannot restore untrashed resources");
      }
      onClearActionBar();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  function onClearActionBar() {
    setOpenedModalName("void");
    setIsActionBarOpen(false);
    deselectAll("all");
  }

  const onFinish = (modalName: ModalName) => () => {
    if (openedModalName === modalName) {
      onClearActionBar();
    }
  };

  const onMoveCancel = onFinish("move");
  const onMoveSuccess = onFinish("move");
  const onDeleteSuccess = onFinish("delete");
  const onDeleteCancel = onFinish("delete");
  const onPublishSuccess = onFinish("publish");
  const onPublishCancel = onFinish("publish");
  const onEditFolderSuccess = onFinish("edit_folder");
  const onEditFolderCancel = onFinish("edit_folder");
  const onEditResourceSuccess = onFinish("edit_resource");
  const onEditResourceCancel = onFinish("edit_resource");

  const trashActions: IAction[] = [
    {
      id: ACTION.DELETE,
      available: true,
      target: "actionbar",
    },
    {
      id: ACTION.RESTORE,
      available: true,
      target: "actionbar",
    },
  ];
  const isTrashFolder = getIsTrashSelected();

  function onEdit() {
    if (selectedResources && selectedResources.length > 0) {
      setOpenedModalName("edit_resource");
    } else {
      setOpenedModalName("edit_folder");
    }
  }

  return {
    selectedResources: getSelectedIResources(),
    actions: isTrashFolder ? trashActions : actions,
    currentFolderId: getCurrentFolderId(),
    handleClick,
    isActivable,
    isActionBarOpen,
    isMoveModalOpen: openedModalName === "move",
    onMoveCancel,
    onMoveSuccess,
    isDeleteModalOpen: openedModalName === "delete",
    onDeleteCancel,
    onDeleteSuccess,
    isPublishModalOpen: openedModalName === "publish",
    onPublishCancel,
    onPublishSuccess,
    isEditFolderOpen: openedModalName === "edit_folder",
    onEditFolderCancel,
    onEditFolderSuccess,
    isEditResourceOpen: openedModalName === "edit_resource",
    onEditResourceCancel,
    onEditResourceSuccess,
    onClearActionBar,
  };
}

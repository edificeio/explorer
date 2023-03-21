import { useState, useEffect } from "react";

import { useOdeClient, Alert } from "@ode-react-ui/core";
import { useHotToast } from "@ode-react-ui/hooks";
import useExplorerStore from "@store/index";
import { type IAction, ACTION } from "ode-ts-client";

type ModalName =
  | "move"
  | "delete"
  | "publish"
  | "edit_folder"
  | "edit_resource"
  | "share"
  | "void";

export default function useActionBar() {
  const [isActionBarOpen, setIsActionBarOpen] = useState<boolean>(false);
  const [openedModalName, setOpenedModalName] = useState<ModalName>("void");

  const { i18n } = useOdeClient();
  const { hotToast } = useHotToast(Alert);
  const {
    actions,
    openFolder,
    getIsTrashSelected,
    getCurrentFolderId,
    openSelectedResource,
    printSelectedResource,
    createResource,
    deselectAll,
    restoreSelection,
    getSelectedIResources,
    getSelectedFolders,
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
    // A11Y: fix Screen readers can read parent page content outside the modal
    // https://docs.deque.com/issue-help/1.0.0/en/reading-order-browse-outside-modal
    document.getElementById("root")?.setAttribute("aria-hidden", "true");

    switch (action.id) {
      case ACTION.OPEN:
        if (selectedResources.length > 0) {
          return openSelectedResource();
        } else {
          return openFolder(selectedFolders[0]);
        }
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
      case ACTION.SHARE:
        return setOpenedModalName("share");
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
    const all = selectedResources.length + selectedFolders.length;
    const onlyOneItemSelected =
      selectedResources.length === 1 || selectedFolders.length === 1;
    const onlyOneSelected = all === 1;
    const noFolderSelected = selectedFolders.length === 0;
    switch (action.id) {
      case ACTION.OPEN:
        return onlyOneSelected;
      case ACTION.MANAGE:
        return onlyOneItemSelected;
      case ACTION.PUBLISH:
        return onlyOneItemSelected && noFolderSelected;
      case ACTION.UPD_PROPS:
        return onlyOneItemSelected && noFolderSelected;
      case ACTION.SHARE:
        return noFolderSelected;
      case ACTION.PRINT:
        return onlyOneItemSelected && noFolderSelected;
      case "edit" as any:
        return onlyOneSelected;
      default:
        return true;
    }
  }
  function isActivableForTrash(action: IAction): boolean {
    return true;
  }

  async function onRestore() {
    try {
      if (getIsTrashSelected()) {
        await restoreSelection();
        hotToast.success(i18n("explorer.trash.toast"));
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
  const onShareResourceSuccess = onFinish("share");
  const onShareResourceCancel = onFinish("share");

  const trashActions: IAction[] = [
    {
      id: ACTION.DELETE,
      available: true,
      target: "actionbar",
      workflow: "",
    },
    {
      id: ACTION.RESTORE,
      available: true,
      target: "actionbar",
      workflow: "",
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

  function overrideLabel(action: IAction) {
    if ((action.id as any) === "edit" && selectedFolders.length > 0) {
      return "explorer.rename";
    }
    return `explorer.actions.${action.id}`;
  }

  return {
    selectedElement: [...getSelectedIResources(), ...getSelectedFolders()],
    actions: isTrashFolder ? trashActions : actions,
    currentFolderId: getCurrentFolderId(),
    overrideLabel,
    handleClick,
    isActivable: isTrashFolder ? isActivableForTrash : isActivable,
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
    isShareResourceOpen: openedModalName === "share",
    onShareResourceCancel,
    onShareResourceSuccess,
    onClearActionBar,
  };
}

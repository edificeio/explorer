import { useState, useEffect } from "react";

import { Alert } from "@ode-react-ui/components";
import { useI18n } from "@ode-react-ui/core";
import { useHotToast } from "@ode-react-ui/hooks";
import { type IAction, ACTION } from "ode-ts-client";

import { useActions, useRestore } from "~/services/queries";
import { getAppParams } from "~/shared/utils/getAppParams";
import {
  useStoreActions,
  useCurrentFolder,
  useFolderIds,
  useIsTrash,
  useResourceIds,
  useSelectedFolders,
  useSelectedResources,
  useResourceIsTrash,
} from "~/store";

type ModalName =
  | "move"
  | "delete"
  | "publish"
  | "edit_folder"
  | "edit_resource"
  | "share"
  | "void";

const { trashActions } = getAppParams();

export default function useActionBar() {
  const [isActionBarOpen, setIsActionBarOpen] = useState<boolean>(false);
  const [openedModalName, setOpenedModalName] = useState<ModalName>("void");
  const [clickedAction, setClickedAction] = useState<IAction>();

  const { i18n } = useI18n();
  const { hotToast } = useHotToast(Alert);

  const currentFolder = useCurrentFolder();
  const resourceIds = useResourceIds();
  const selectedResources = useSelectedResources();
  const selectedFolders = useSelectedFolders();
  const folderIds = useFolderIds();
  const isTrashFolder = useIsTrash();
  const restoreItem = useRestore();
  const isTrashResource = useResourceIsTrash();
  const {
    openResource,
    createResource,
    printSelectedResource,
    openFolder,
    clearSelectedItems,
    clearSelectedIds,
  } = useStoreActions();

  const { data: actions } = useActions();

  useEffect(() => {
    if (resourceIds.length === 0 && folderIds.length === 0) {
      setIsActionBarOpen(false);
      return;
    }
    if (isTrashResource) {
      setIsActionBarOpen(false);
      return;
    }
    setIsActionBarOpen(true);
  }, [resourceIds, folderIds]);

  async function handleClick(action: IAction) {
    // A11Y: fix Screen readers can read parent page content outside the modal
    // https://docs.deque.com/issue-help/1.0.0/en/reading-order-browse-outside-modal
    document.getElementById("root")?.setAttribute("aria-hidden", "true");

    // A11Y: save clicked action to set focus to clicked button after modal is dismissed
    setClickedAction(action);

    switch (action.id) {
      case ACTION.OPEN:
        if (resourceIds.length > 0) {
          return openResource(selectedResources[0]);
        } else {
          return openFolder({
            folder: selectedFolders[0],
            folderId: selectedFolders[0].id,
          });
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
        return await onRestore();
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
    const all = resourceIds.length + folderIds.length;
    const onlyOneItemSelected =
      resourceIds.length === 1 || folderIds.length === 1;
    const onlyOneSelected = all === 1;
    const noFolderSelected = folderIds.length === 0;
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
        return noFolderSelected && onlyOneItemSelected;
      case ACTION.PRINT:
        return onlyOneItemSelected && noFolderSelected;
      case "edit" as any:
        return onlyOneSelected;
      default:
        return true;
    }
  }
  function isActivableForTrash(): boolean {
    return true;
  }

  async function onRestore() {
    try {
      if (isTrashFolder) {
        await restoreItem.mutate();
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
    // a11y: set focus to action button after modal is dismissed
    if (clickedAction?.id) {
      document.getElementById(clickedAction?.id)?.focus();
    }
  }

  const onFinish = (modalName: ModalName) => () => {
    if (openedModalName === modalName) {
      onClearActionBar();
      clearSelectedItems();
      clearSelectedIds();
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

  function onEdit() {
    if (resourceIds && resourceIds.length > 0) {
      setOpenedModalName("edit_resource");
    } else {
      setOpenedModalName("edit_folder");
    }
  }

  function overrideLabel(action: IAction) {
    if ((action.id as any) === "edit" && folderIds.length > 0) {
      return "explorer.rename";
    }

    return `explorer.actions.${action.id}`;
  }

  return {
    onRestore,
    actions: isTrashFolder ? trashActions : actions,
    selectedElement: [...selectedResources, ...selectedFolders],
    currentFolderId: currentFolder?.id,
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

import { useState, useEffect } from "react";

import { useExplorerContext } from "@contexts/ExplorerContext/ExplorerContext";
import { IAction, ACTION, FOLDER } from "ode-ts-client";

type ModalName = "move" | "delete" | "publish" | "void";

export default function useActionBar() {
  const [actions, setActions] = useState<IAction[]>([]);
  const [isActionBarOpen, setIsActionBarOpen] = useState<boolean>(false);
  const [openedModalName, setOpenedModalName] = useState<ModalName>("void");

  const {
    openResource,
    printResource,
    createResource,
    hideSelectedElement,
    deselectAllResources,
    deselectAllFolders,
    contextRef,
    selectedResources,
    selectedFolders,
    state,
  } = useExplorerContext();
  const parentFolder = state.folders.find(
    (e) => e.id === contextRef.current.getSearchParameters().filters.folder,
  );
  useEffect(() => {
    const ref = contextRef.current;
    const refActions = ref?.getContext()?.actions;
    setActions(refActions!);
  }, []);

  useEffect(() => {
    if (selectedResources.length === 0 && selectedFolders.length === 0) {
      setIsActionBarOpen(false);
      return;
    }
    setIsActionBarOpen(true);
  }, [selectedResources]);

  function handleClick(action: IAction) {
    switch (action.id) {
      case ACTION.OPEN:
        return openResource();
      case ACTION.CREATE:
        return createResource();
      case ACTION.MOVE:
        return setOpenedModalName("move");
      case ACTION.PRINT:
        return printResource();
      case ACTION.DELETE:
        return setOpenedModalName("delete");
      case ACTION.RESTORE:
        return onRestore();
      case ACTION.PUBLISH:
        return setOpenedModalName("publish");
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
      selectedResources.length === 1 && selectedFolders.length === 0;
    switch (action.id) {
      case ACTION.OPEN:
        return onlyOneItemSelected;
      case ACTION.SHARE:
        return onlyOneItemSelected;
      case ACTION.MANAGE:
        return onlyOneItemSelected;
      case ACTION.PUBLISH:
        return onlyOneItemSelected;
      default:
        return true;
    }
  }

  async function onRestore() {
    try {
      const isAlreadyInTrash =
        contextRef.current.getSearchParameters().filters.folder === FOLDER.BIN;
      const resourceIds = selectedResources.map((e) => e.id);
      const folderIds = selectedFolders.map((e) => e.id);
      if (isAlreadyInTrash) {
        await contextRef.current.trash(false, resourceIds, folderIds);
      } else {
        throw new Error("Cannot restore untrashed resources");
      }
      onClearActionBar();
      hideSelectedElement();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  function onClearActionBar() {
    setOpenedModalName("void");
    setIsActionBarOpen(false);
    deselectAllResources();
    deselectAllFolders();
  }

  function onMoveCancel() {
    if (openedModalName === "move") {
      onClearActionBar();
    }
  }

  function onMoveSuccess() {
    if (openedModalName === "move") {
      onClearActionBar();
      hideSelectedElement();
    }
  }

  function onDeleteCancel() {
    if (openedModalName === "delete") {
      onClearActionBar();
    }
  }

  function onPublishCancel() {
    if (openedModalName === "publish") {
      onClearActionBar();
    }
  }

  function onDeleteSuccess() {
    if (openedModalName === "delete") {
      onClearActionBar();
      hideSelectedElement();
    }
  }
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
  const isTrashFolder =
    contextRef.current.getSearchParameters().filters.folder === FOLDER.BIN;

  function onPublishSuccess() {
    if (openedModalName === "publish") {
      onClearActionBar();
    }
  }

  return {
    actions: isTrashFolder ? trashActions : actions,
    parentFolder,
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
  };
}

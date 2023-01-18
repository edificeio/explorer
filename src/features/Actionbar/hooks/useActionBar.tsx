import { useState, useEffect } from "react";

import { useExplorerContext } from "@contexts/ExplorerContext/ExplorerContext";
import { IAction, ACTION } from "ode-ts-client";

type ModalName = "move" | "delete" | "void";

export default function useActionBar(isOpen?: boolean) {
  const [actions, setActions] = useState<IAction[]>([]);
  const [isActionBarOpen, setIsActionBarOpen] = useState<boolean>(
    isOpen || false,
  );
  const [openedModalName, setOpenedModalName] = useState<ModalName>("void");

  const {
    openResource,
    printResource,
    createResource,
    refreshFolder,
    deselectAllResources,
    context,
    selectedResources,
    selectedFolders,
  } = useExplorerContext();

  useEffect(() => {
    const ctx = context.getContext();
    if (ctx?.actions) {
      setActions(ctx.actions);
    }
  }, [context]);

  useEffect(() => {
    if (selectedResources.length === 0) {
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
      // case ACTION.SHARE:
      //   return explorer.onShare();
      // case ACTION.MANAGE:
      //   return explorer.onManage();
      default:
        throw Error("Unknown action: " + action.id);
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
      default:
        return true;
    }
  }

  function onClearActionBar() {
    setOpenedModalName("void");
    setIsActionBarOpen(false);
    deselectAllResources();
  }

  function onMoveCancel() {
    if (openedModalName === "move") {
      onClearActionBar();
    }
  }

  function onMoveSuccess() {
    if (openedModalName === "move") {
      onClearActionBar();
      refreshFolder();
    }
  }

  return {
    actions,
    isActivable,
    handleClick,
    isActionBarOpen,
    isMoveModalOpen: openedModalName === "move",
    onMoveCancel,
    onMoveSuccess,
  };
}

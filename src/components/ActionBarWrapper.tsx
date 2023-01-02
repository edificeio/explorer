import { useEffect, useState } from "react";

import { useExplorerContext } from "@contexts/ExplorerContext";
import { Button, ActionBar } from "@ode-react-ui/core";
import { ACTION, IAction } from "ode-ts-client";

export default function FakeToaster({ isOpen }: { isOpen: boolean }) {
  const [actions, setActions] = useState<Array<IAction>>([]);
  const explorer = useExplorerContext();

  useEffect(() => {
    const ctx = explorer.context.getContext();
    if (ctx && ctx.actions) {
      setActions(ctx.actions);
    }
  }, [explorer.context]);

  function handleClick(action: IAction) {
    switch (action.id) {
      case ACTION.OPEN:
        return explorer.onOpen();
      case ACTION.CREATE:
        return explorer.onCreate();
      // case ACTION.SHARE:
      //   return explorer.onShare();
      // case ACTION.MANAGE:
      //   return explorer.onManage();
      default:
        return Promise.reject();
    }
  }

  /**
   * Visibility rules for the action buttons.
   * @param action action to check
   * @returns true if the action button must be visible
   */
  function isActivable(action: IAction): boolean {
    const onlyOneItemSelected =
      explorer.selectedResources.length === 1 &&
      explorer.selectedFolders.length === 0;
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

  function generateButtonFor(action: IAction) {
    return (
      <Button
        key={action.id}
        type="button"
        color="primary"
        variant="filled"
        disabled={!isActivable(action)}
        // eslint-disable-next-line react/jsx-no-bind
        onClick={() => handleClick(action)}
      >
        {action.id}
      </Button>
    );
  }

  return isOpen ? (
    <div className="position-fixed bottom-0 start-0 end-0">
      <ActionBar>
        {actions
          .filter((action) => action.available)
          .map((action: IAction) => generateButtonFor(action))}
      </ActionBar>
    </div>
  ) : null;
}

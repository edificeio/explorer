import { Button, useOdeClient } from "@ode-react-ui/core";
import { Plus } from "@ode-react-ui/icons";
import { useActions } from "@services/queries";
import { useStoreActions } from "@store/store";
import { type IAction } from "ode-ts-client";

export function AppAction() {
  const { i18n } = useOdeClient();
  const { data: actions } = useActions();
  const { createResource } = useStoreActions();

  function isActionAvailable(value: string) {
    const found = actions?.filter(
      (action: IAction) => action.id === value && action.available,
    );
    return found && found.length > 0;
  }

  return (
    <>
      {isActionAvailable("create") && (
        <Button
          type="button"
          color="primary"
          variant="filled"
          leftIcon={<Plus />}
          className="ms-auto"
          onClick={createResource}
        >
          {i18n("explorer.create.title")}
        </Button>
      )}
    </>
  );
}

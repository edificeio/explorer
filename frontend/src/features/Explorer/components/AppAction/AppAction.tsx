import { Button } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { Plus } from "@ode-react-ui/icons";
import { useStoreActions } from "@store/store";

export default function AppAction() {
  const { i18n } = useOdeClient();
  const { createResource } = useStoreActions();

  return (
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
  );
}

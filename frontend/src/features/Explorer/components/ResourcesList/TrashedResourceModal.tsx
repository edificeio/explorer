import { Modal, Button } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { createPortal } from "react-dom";

import useActionBar from "~/features/Actionbar/hooks/useActionBar";
import { useStoreActions } from "~/store";

export default function TrashedResourceModal({
  isOpen,
  onCancel = () => ({}),
}: {
  isOpen: boolean;
  onCancel?: () => void;
}) {
  const { i18n } = useOdeClient();
  const { onRestore, actions } = useActionBar();

  const { setResourceIsTrash } = useStoreActions();

  async function restoreTrashResource() {
    await onRestore();
    setResourceIsTrash(false);
  }

  const rightToRestore = actions?.some((action) => action.id === "restore");

  return createPortal(
    <Modal isOpen={isOpen} onModalClose={onCancel} id="trash_resource">
      <Modal.Header
        onModalClose={() => {
          // TODO fix onModalClose type to avoid this hack
          onCancel();
          return {};
        }}
      >
        {i18n("Accès à la ressource")}
      </Modal.Header>
      <Modal.Body>
        <p className="body">
          Les ressources placées dans la corbeille ne sont pas consultables.
          Veuillez restaurer la ressource ou demander à l’auteur ou un
          gestionnaire de le faire pour vous afin de pouvoir la consulter
        </p>
      </Modal.Body>
      <Modal.Footer>
        <Button
          color="primary"
          onClick={onCancel}
          type="button"
          variant="outline"
        >
          {i18n("Fermer")}
        </Button>
        {rightToRestore && (
          <Button
            color="primary"
            onClick={async () => await restoreTrashResource()}
            type="button"
            variant="filled"
          >
            {i18n("Restaurer")}
          </Button>
        )}
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}

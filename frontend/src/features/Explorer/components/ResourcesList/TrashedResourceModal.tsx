import { Modal, Button } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { createPortal } from "react-dom";

import useActionBar from "~/features/Actionbar/hooks/useActionBar";
import { useSelectedResources, useStoreActions } from "~/store";

export default function TrashedResourceModal({
  isOpen,
  onCancel = () => ({}),
}: {
  isOpen: boolean;
  onCancel?: () => void;
}) {
  const { i18n } = useOdeClient();
  const { onRestore } = useActionBar();

  const { setResourceIsTrash } = useStoreActions();
  const selectedResources = useSelectedResources();

  async function restoreTrashResource() {
    await onRestore();
    setResourceIsTrash(false);
  }

  return createPortal(
    <Modal isOpen={isOpen} onModalClose={onCancel} id="trash_resource">
      <Modal.Header onModalClose={() => onCancel()}>
        {i18n("explorer.trash.modal.title")}
      </Modal.Header>
      <Modal.Body>
        <p className="body">{i18n("explorer.trash.modal.text")}</p>
      </Modal.Body>
      <Modal.Footer>
        <Button
          color="primary"
          onClick={onCancel}
          type="button"
          variant="outline"
        >
          {i18n("close")}
        </Button>
        {selectedResources[0]?.trashedBy.length === 0 && (
          <Button
            color="primary"
            onClick={async () => await restoreTrashResource()}
            type="button"
            variant="filled"
          >
            {i18n("restore")}
          </Button>
        )}
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}

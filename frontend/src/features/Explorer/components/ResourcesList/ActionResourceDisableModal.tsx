import { Modal, Button } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { createPortal } from "react-dom";

export default function ActionResourceDisableModal({
  isOpen,
  onCancel = () => ({}),
}: {
  isOpen: boolean;
  onCancel?: () => void;
}) {
  const { i18n } = useOdeClient();

  return createPortal(
    <Modal
      isOpen={isOpen}
      onModalClose={onCancel}
      id="trash_action_disable_resource"
    >
      <Modal.Header onModalClose={() => onCancel()}>
        {i18n("explorer.trash.action.modal.title")}
      </Modal.Header>
      <Modal.Body>
        <p className="body">{i18n("explorer.trash.action.modal.text")}</p>
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
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}

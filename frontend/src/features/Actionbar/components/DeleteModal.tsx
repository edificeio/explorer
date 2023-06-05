import { Modal, Button } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { createPortal } from "react-dom";

import useDeleteModal from "../hooks/useDeleteModal";

interface ModalProps {
  isOpen: boolean;
  onSuccess?: () => void;
  onCancel?: () => void;
}

export default function DeleteModal({
  isOpen,
  onSuccess = () => ({}),
  onCancel = () => ({}),
}: ModalProps) {
  const { i18n } = useOdeClient();
  const { isTrashFolder, onDelete } = useDeleteModal({
    onSuccess,
  });
  return createPortal(
    <Modal isOpen={isOpen} onModalClose={onCancel} id="deleteModal">
      <Modal.Header onModalClose={onCancel}>
        {i18n(isTrashFolder ? "explorer.delete.title" : "explorer.trash.title")}
      </Modal.Header>
      <Modal.Body>
        <p className="body">
          {i18n(
            isTrashFolder
              ? "explorer.delete.subtitle"
              : "explorer.trash.subtitle",
          )}
        </p>
      </Modal.Body>
      <Modal.Footer>
        <Button
          color="tertiary"
          onClick={onCancel}
          type="button"
          variant="ghost"
        >
          {i18n("explorer.cancel")}
        </Button>
        <Button
          color="primary"
          onClick={onDelete}
          type="button"
          variant="filled"
        >
          {i18n(isTrashFolder ? "explorer.delete" : "explorer.trash")}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}

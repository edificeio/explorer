import useDeleteModal from "@features/Actionbar/hooks/useDeleteModal";
import { Modal, Button, useOdeClient } from "@ode-react-ui/core";

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
  return (
    <Modal isOpen={isOpen} onModalClose={onCancel} id="deleteModal">
      <Modal.Header
        onModalClose={() => {
          // TODO fix onModalClose type to avoid this hack
          onCancel();
          return {};
        }}
      >
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
    </Modal>
  );
}

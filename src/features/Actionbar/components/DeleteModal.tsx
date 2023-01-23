import { useExplorerContext } from "@contexts/index";
import useDeleteModal from "@features/Actionbar/hooks/useDeleteModal";
import { Modal, Button } from "@ode-react-ui/core";

interface DeleteModalProps {
  isOpen: boolean;
  onSuccess?: () => void;
  onCancel?: () => void;
}

export default function DeleteModal({
  isOpen,
  onSuccess = () => ({}),
  onCancel = () => ({}),
}: DeleteModalProps) {
  const { i18n } = useExplorerContext();

  const { isTrash, onDelete } = useDeleteModal({ onSuccess });
  return (
    <Modal isOpen={isOpen} onModalClose={onCancel} id="deleteModal">
      <Modal.Header
        onModalClose={() => {
          // TODO fix onModalClose type to avoid this hack
          onCancel();
          return {};
        }}
      >
        {i18n(isTrash ? "explorer.trash.title" : "explorer.delete.title")}
      </Modal.Header>
      <Modal.Subtitle>
        {i18n(isTrash ? "explorer.trash.subtitle" : "explorer.delete.subtitle")}
      </Modal.Subtitle>
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
          onClick={(_) => onDelete()}
          type="button"
          variant="filled"
        >
          {i18n(isTrash ? "explorer.trash" : "explorer.delete")}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

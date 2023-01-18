import useDeleteModal from "@features/Actionbar/hooks/useDeleteModal";
import { useI18n } from "@hooks/useI18n";
import { Modal, Button } from "@ode-react-ui/core";

interface DeleteModalProps {
  isOpen: boolean;
  onSuccess?: () => void;
  onCancel?: () => void;
}

export default function DeleteModal({
  isOpen,
  onSuccess = () => {},
  onCancel = () => {},
}: DeleteModalProps) {
  const { i18n } = useI18n();
  const { onDelete } = useDeleteModal({ onSuccess });
  return (
    <Modal isOpen={isOpen} onModalClose={onCancel} id="deleteModal">
      <Modal.Header onModalClose={onCancel}>
        {i18n("explorer.delete.title")}
      </Modal.Header>
      <Modal.Subtitle>{i18n("explorer.delete.subtitle")}</Modal.Subtitle>
      <Modal.Footer>
        <Button
          color="tertiary"
          onClick={onCancel}
          type="button"
          variant="ghost"
        >
          {i18n("cancel")}
        </Button>
        <Button
          color="primary"
          onClick={(_) => onDelete()}
          type="button"
          variant="filled"
        >
          {i18n("move")}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

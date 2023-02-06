import useRestoreModal from "@features/Actionbar/hooks/useRestoreModal";
import { Modal, Button, useOdeClient } from "@ode-react-ui/core";

interface RestoreModalProps {
  isOpen: boolean;
  onSuccess?: () => void;
  onCancel?: () => void;
}

export default function RestoreModal({
  isOpen,
  onSuccess = () => ({}),
  onCancel = () => ({}),
}: RestoreModalProps) {
  const { i18n } = useOdeClient();
  const { onRestore } = useRestoreModal({
    onSuccess,
  });
  return (
    <Modal isOpen={isOpen} onModalClose={onCancel} id="RestoreModal">
      <Modal.Header
        onModalClose={() => {
          // TODO fix onModalClose type to avoid this hack
          onCancel();
          return {};
        }}
      >
        {i18n("explorer.restore.title")}
      </Modal.Header>
      <Modal.Body>
        <p className="body">{i18n("explorer.restore.subtitle")}</p>
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
          onClick={(_) => onRestore()}
          type="button"
          variant="filled"
        >
          {i18n("explorer.restore")}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

import { useExplorerContext } from "@contexts/index";
import useCreateModal from "@features/Actionbar/hooks/useCreateModal";
import { Modal, Button, Heading } from "@ode-react-ui/core";

interface CreateModalProps {
  isOpen: boolean;
  onSuccess?: () => void;
  onCancel?: () => void;
}

export default function CreateModal({
  isOpen,
  onSuccess = () => ({}),
  onCancel = () => ({}),
}: CreateModalProps) {
  const { i18n } = useExplorerContext();
  const { onCreate } = useCreateModal({
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
        {i18n("explorer.create.title")}
      </Modal.Header>
      <Modal.Body>
        <Heading className="mb-16" headingStyle="h4" level="h3">
          {i18n("explorer.create.subtitle")}
        </Heading>
        <p className="body"></p>
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
          onClick={(_) => onCreate()}
          type="button"
          variant="filled"
        >
          {i18n("explorer.create")}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

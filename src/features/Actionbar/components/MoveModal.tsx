import useMoveModal from "@features/Actionbar/hooks/useMoveModal";
import { useI18n } from "@hooks/useI18n";
import { Modal, Button, TreeView } from "@ode-react-ui/core";

interface MoveModalProps {
  isOpen: boolean;
  onSuccess?: () => void;
  onCancel?: () => void;
}

export default function MoveModal({
  isOpen,
  onSuccess = () => {},
  onCancel = () => {},
}: MoveModalProps) {
  const { i18n } = useI18n();
  const {
    handleTreeItemFold,
    handleTreeItemSelect,
    handleTreeItemUnfold,
    onMove,
    treeData,
  } = useMoveModal({ onSuccess });
  return (
    <Modal isOpen={isOpen} onModalClose={onCancel} id="moveModal">
      <Modal.Header onModalClose={onCancel}>
        {i18n("blog.move.title")}
      </Modal.Header>
      <Modal.Subtitle>{i18n("blog.move.subtitle")}</Modal.Subtitle>
      <Modal.Body>
        <TreeView
          data={treeData}
          onTreeItemSelect={handleTreeItemSelect}
          onTreeItemFold={handleTreeItemFold}
          onTreeItemUnfold={handleTreeItemUnfold}
        />
      </Modal.Body>
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
          onClick={(_) => onMove()}
          type="button"
          variant="filled"
        >
          {i18n("move")}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

import { Modal, Button, TreeView } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { createPortal } from "react-dom";

import useMoveModal from "../hooks/useMoveModal";
import { useTreeData } from "~store/store";

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
  const { i18n } = useOdeClient();
  const {
    handleTreeItemFold,
    handleTreeItemSelect,
    handleTreeItemUnfold,
    onMove,
    disableSubmit,
  } = useMoveModal({ onSuccess });

  const treeData = useTreeData();

  return createPortal(
    <Modal isOpen={isOpen} onModalClose={onCancel} id="moveModal">
      <Modal.Header
        onModalClose={() => {
          // TODO fix onModalClose type to avoid this hack
          onCancel();
          return {};
        }}
      >
        {i18n("explorer.move.title")}
      </Modal.Header>
      <Modal.Subtitle>{i18n("explorer.move.subtitle")}</Modal.Subtitle>
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
          {i18n("explorer.cancel")}
        </Button>
        <Button
          color="primary"
          onClick={onMove}
          type="button"
          variant="filled"
          disabled={disableSubmit}
        >
          {i18n("explorer.move")}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}

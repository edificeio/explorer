import { Button, Modal, TreeView, useEdificeClient } from '@edifice.io/react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';

import { useMoveModal } from './useMoveModal';

interface MoveModalProps {
  isOpen: boolean;
  onSuccess: () => void;
  onCancel: () => void;
}

export default function MoveModal({
  isOpen,
  onSuccess,
  onCancel,
}: MoveModalProps) {
  const { appCode } = useEdificeClient();
  const { t } = useTranslation();
  const {
    treeData,
    handleTreeItemSelect,
    handleOnTreeItemUnfold,
    onMove,
    disableSubmit,
  } = useMoveModal({
    onSuccess,
  });

  const data = {
    ...treeData,
    name: t('explorer.filters.mine', { ns: appCode }),
  };

  return createPortal(
    <Modal isOpen={isOpen} onModalClose={onCancel} id="moveModal">
      <Modal.Header onModalClose={onCancel}>
        {t('explorer.move.title')}
      </Modal.Header>
      <Modal.Subtitle>{t('explorer.move.subtitle')}</Modal.Subtitle>
      <Modal.Body>
        <TreeView
          data={data}
          onTreeItemClick={handleTreeItemSelect}
          onTreeItemUnfold={handleOnTreeItemUnfold}
        />
      </Modal.Body>
      <Modal.Footer>
        <Button
          color="tertiary"
          onClick={onCancel}
          type="button"
          variant="ghost"
        >
          {t('explorer.cancel')}
        </Button>
        <Button
          color="primary"
          onClick={onMove}
          type="button"
          variant="filled"
          disabled={disableSubmit}
        >
          {t('explorer.move')}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById('portal') as HTMLElement,
  );
}

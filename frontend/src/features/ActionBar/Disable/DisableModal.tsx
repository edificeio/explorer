import { Modal, Button } from '@edifice.io/react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';

export default function DisableModal({
  isOpen,
  onCancel = () => ({}),
}: {
  isOpen: boolean;
  onCancel?: () => void;
}) {
  const { t } = useTranslation();

  return createPortal(
    <Modal
      isOpen={isOpen}
      onModalClose={onCancel}
      id="trash_action_disable_resource"
    >
      <Modal.Header onModalClose={() => onCancel()}>
        {t('explorer.trash.action.modal.title')}
      </Modal.Header>
      <Modal.Body>
        <p className="body">{t('explorer.trash.action.modal.text')}</p>
      </Modal.Body>
      <Modal.Footer>
        <Button
          color="primary"
          onClick={onCancel}
          type="button"
          variant="outline"
        >
          {t('close')}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById('portal') as HTMLElement,
  );
}

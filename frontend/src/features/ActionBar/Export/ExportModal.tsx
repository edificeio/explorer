import { Alert, Button, Modal } from '@edifice-ui/react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';

import { useExportModal } from './useExportModal';

interface ExportModalProps {
  isOpen: boolean;
  onSuccess: () => void;
  onCancel: () => void;
}

/**
 * HTML Export Modal (for Scrapbook)
 */
export default function ExportModal({
  isOpen,
  onSuccess,
  onCancel,
}: ExportModalProps) {
  const { handleExportClick } = useExportModal(onSuccess);
  const { t } = useTranslation();

  return createPortal(
    <Modal isOpen={isOpen} onModalClose={onCancel} id="exportModal">
      <Modal.Header onModalClose={onCancel}>
        {t('explorer.export.title')}
      </Modal.Header>
      <Modal.Body>
        <ul>
          <li>{t('explorer.export.body.info.1')}</li>
          <li>{t('explorer.export.body.info.2')}</li>
          <li>{t('explorer.export.body.info.3')}</li>
        </ul>
        <Alert type="warning">{t('explorer.export.body.warning')}</Alert>
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
          onClick={handleExportClick}
          type="button"
          variant="filled"
        >
          {t('explorer.actions.export')}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById('portal') as HTMLElement,
  );
}

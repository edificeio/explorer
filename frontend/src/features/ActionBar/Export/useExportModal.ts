import { useToast } from '@edifice-ui/react';
import { useTranslation } from 'react-i18next';

import { goToExport } from '~/services/api';
import { useSearchParams, useSelectedResources } from '~/store';

export const useExportModal = (onSuccess: () => void) => {
  const selectedResources = useSelectedResources();
  const searchParams = useSearchParams();

  const toast = useToast();

  const { t } = useTranslation();

  const handleExportClick = () => {
    if (selectedResources.length) {
      goToExport({ searchParams, assetId: selectedResources[0].assetId });
      onSuccess();
    } else {
      toast.error(t('explorer.error.noResourceSelected'));
    }
  };

  return { handleExportClick };
};

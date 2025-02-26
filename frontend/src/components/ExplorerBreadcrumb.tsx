import { IconButton, useEdificeClient } from '@edifice.io/react';
import { IconArrowLeft } from '@edifice.io/react/icons';
import { useTranslation } from 'react-i18next';

import { useSearchForm } from '~/features/SearchForm/useSearchForm';
import {
  useCurrentFolder,
  useIsTrash,
  useSelectedNodeId,
  useStoreActions,
} from '~/store';

export function ExplorerBreadcrumb() {
  const { appCode } = useEdificeClient();
  const { gotoPreviousFolder } = useStoreActions();
  const { t } = useTranslation(['common', appCode]);
  const { inputSearch } = useSearchForm();

  const selectedNodeId = useSelectedNodeId();
  const isTrashFolder = useIsTrash();
  const currentFolder = useCurrentFolder();

  const trashName: string = t('explorer.tree.trash');
  const searchName: string = t('explorer.tree.search');
  const rootName: string = t('explorer.filters.mine', {
    ns: appCode,
  });
  const previousName: string = currentFolder?.name || rootName;

  return (
    <div className="py-16">
      {selectedNodeId &&
      selectedNodeId !== 'bin' &&
      selectedNodeId !== 'default' &&
      !isTrashFolder ? (
        <div className="d-flex align-items-center gap-8">
          <IconButton
            icon={<IconArrowLeft />}
            variant="ghost"
            color="tertiary"
            aria-label={t('back')}
            className="ms-n16"
            onClick={gotoPreviousFolder}
          />
          <p className="body py-8 text-truncate">
            <strong>{previousName}</strong>
          </p>
        </div>
      ) : (
        <h2 className="body py-8 fw-bold">
          {inputSearch.length !== 0
            ? searchName
            : !isTrashFolder
              ? rootName
              : trashName}
        </h2>
      )}
    </div>
  );
}

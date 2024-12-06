import { Suspense, lazy } from 'react';

import { IAction, isActionAvailable } from '@edifice.io/client';
import {
  Button,
  LoadingScreen,
  useEdificeClient,
  useToggle,
} from '@edifice.io/react';
import { IconPlus } from '@edifice.io/react/icons';
import { BlogPublic } from '@edifice.io/react/modals';
import { useTranslation } from 'react-i18next';

import { goToCreate } from '~/services/api';
import { useActions, useCreateResource } from '~/services/queries';
import {
  useCurrentFolder,
  useSearchParams,
  useStoreActions,
  useStoreContext,
} from '~/store';

const CreateModal = lazy(
  async () => await import('~/features/ActionBar/Resource/ResourceModal'),
);

export default function AppAction() {
  const [isCreateResourceOpen, toggle] = useToggle();

  const config = useStoreContext((state) => state.config);

  const { appCode } = useEdificeClient();
  const { t } = useTranslation(appCode);

  const { clearSelectedItems, clearSelectedIds } = useStoreActions();
  const { data: actions } = useActions(config?.actions as IAction[]);

  const canCreate = actions?.find((action: IAction) => action.id === 'create');

  const currentFolder = useCurrentFolder();
  const createResource = useCreateResource();
  const searchParams = useSearchParams();

  const handleOnResourceCreate = () => {
    if (appCode == 'scrapbook') {
      goToCreate({ searchParams, folderId: currentFolder.id });
      return;
    }
    clearSelectedItems();
    clearSelectedIds();
    toggle();
  };

  return canCreate ? (
    <>
      <Button
        type="button"
        color="primary"
        variant="filled"
        leftIcon={<IconPlus />}
        className="ms-auto"
        onClick={handleOnResourceCreate}
      >
        {t('explorer.create.title')}
      </Button>

      <Suspense fallback={<LoadingScreen />}>
        {isCreateResourceOpen && (
          <CreateModal
            mode="create"
            currentFolder={currentFolder}
            createResource={createResource}
            isOpen={isCreateResourceOpen}
            onSuccess={toggle}
            onCancel={toggle}
          >
            {(resource, isUpdating, watch, setValue, register) =>
              appCode === 'blog' &&
              isActionAvailable('createPublic', actions) && (
                <BlogPublic
                  appCode={appCode}
                  isUpdating={isUpdating}
                  resource={resource}
                  watch={watch}
                  setValue={setValue}
                  register={register}
                />
              )
            }
          </CreateModal>
        )}
      </Suspense>
    </>
  ) : null;
}

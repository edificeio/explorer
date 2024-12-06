import { Suspense, lazy, useEffect } from 'react';

import { DndContext } from '@dnd-kit/core';
import { snapCenterToCursor } from '@dnd-kit/modifiers';
import { IWebApp, isActionAvailable } from '@edifice.io/client';
import {
  AppHeader,
  Breadcrumb,
  Grid,
  LoadingScreen,
  useEdificeClient,
  useXitiTrackPageLoad,
} from '@edifice.io/react';

import { AppParams } from '~/config';
import { useDisableModal } from '~/features/ActionBar/Disable/useDisableModal';
import { useTrashModal } from '~/features/ActionBar/Trash/useTrashModal';
import useDndKit from '~/features/DndKit/useDndKit';
import { List } from '~/features/List/List';
import { SearchForm } from '~/features/SearchForm/SearchForm';
import { TreeViewContainer } from '~/features/SideBar/TreeViewContainer';
import { useActions } from '~/services/queries';
import { useSearchParams, useStoreActions } from '~/store';
import { ExplorerBreadcrumb } from './ExplorerBreadcrumb';

import illuTrashDelete from '@images/onboarding/illu-trash-delete.svg';
import illuTrashMenu from '@images/onboarding/illu-trash-menu.svg';
import illuTrashNotif from '@images/onboarding/illu-trash-notif.svg';

const AppAction = lazy(
  async () => await import('~/components/AppAction/AppAction'),
);

const Library = lazy(
  async () => await import('~/features/SideBar/Library/Library'),
);

const ActionBar = lazy(
  async () => await import('~/features/ActionBar/ActionBarContainer'),
);
const DisableModal = lazy(
  async () => await import('~/features/ActionBar/Disable/DisableModal'),
);

const TrashModal = lazy(
  async () => await import('~/features/ActionBar/Trash/TrashModal'),
);

const OnboardingModal = lazy(async () => await import('./OnboardingModal'));

const Explorer = ({ config }: { config: AppParams }) => {
  const searchParams = useSearchParams();
  const { setConfig, setSearchParams } = useStoreActions();

  useEffect(() => {
    setConfig(config || {});
    setSearchParams({
      ...searchParams,
      application: config.app,
      types: config.types,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [config]);

  const { currentApp } = useEdificeClient();
  const { data: actions } = useActions(config.actions);
  const { isTrashedModalOpen, onTrashedCancel } = useTrashModal();
  const { isActionDisableModalOpen, onActionDisableCancel } = useDisableModal();
  const { handleDragEnd, handleDragOver, handleDragStart, sensors } =
    useDndKit();

  useXitiTrackPageLoad();

  const canPublish = isActionAvailable('publish', actions);
  const canCreate = isActionAvailable('create', actions);

  return (
    config && (
      <>
        <AppHeader
          render={() =>
            canCreate ? (
              <Suspense fallback={<LoadingScreen />}>
                <AppAction />
              </Suspense>
            ) : null
          }
        >
          <Breadcrumb app={currentApp as IWebApp} />
        </AppHeader>

        <Grid className="flex-grow-1">
          <DndContext
            sensors={sensors}
            modifiers={[snapCenterToCursor]}
            onDragStart={handleDragStart}
            onDragEnd={handleDragEnd}
            onDragOver={handleDragOver}
          >
            <Grid.Col
              sm="3"
              lg="2"
              xl="3"
              className="border-end pt-16 pe-16 d-none d-lg-block"
              as="aside"
            >
              <Suspense fallback={<LoadingScreen />}>
                <TreeViewContainer />
                {canPublish && <Library />}
              </Suspense>
            </Grid.Col>
            <Grid.Col sm="4" md="8" lg="6" xl="9">
              <SearchForm />
              <ExplorerBreadcrumb />
              <List />
            </Grid.Col>
            <Suspense fallback={<LoadingScreen />}>
              <ActionBar />
              {config.enableOnboarding && (
                <OnboardingModal
                  id="showOnboardingTrash"
                  items={[
                    {
                      src: illuTrashMenu,
                      alt: 'explorer.modal.onboarding.trash.screen1.alt',
                      text: 'explorer.modal.onboarding.trash.screen1.title',
                    },
                    {
                      src: illuTrashNotif,
                      alt: 'explorer.modal.onboarding.trash.screen2.alt',
                      text: 'explorer.modal.onboarding.trash.screen2.alt',
                    },
                    {
                      src: illuTrashDelete,
                      alt: 'explorer.modal.onboarding.trash.screen3.alt',
                      text: 'explorer.modal.onboarding.trash.screen3.title',
                    },
                  ]}
                  modalOptions={{
                    title: 'explorer.modal.onboarding.trash.title',
                    prevText: 'explorer.modal.onboarding.trash.prev',
                    nextText: 'explorer.modal.onboarding.trash.next',
                    closeText: 'explorer.modal.onboarding.trash.close',
                  }}
                />
              )}
              {isTrashedModalOpen && (
                <TrashModal
                  isOpen={isTrashedModalOpen}
                  onCancel={onTrashedCancel}
                />
              )}
              {isActionDisableModalOpen && (
                <DisableModal
                  isOpen={isActionDisableModalOpen}
                  onCancel={onActionDisableCancel}
                />
              )}
            </Suspense>
          </DndContext>
        </Grid>
      </>
    )
  );
};

export default Explorer;

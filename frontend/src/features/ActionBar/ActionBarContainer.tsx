import { lazy, Suspense } from 'react';

import { type IAction, isActionAvailable } from '@edifice.io/client';
import {
  ActionBar,
  BlogPublic,
  Button,
  LoadingScreen,
  ShareBlog,
  ShareModal,
  useEdificeClient,
} from '@edifice.io/react';
import { animated, useTransition } from '@react-spring/web';
import { useTranslation } from 'react-i18next';

// import ShareBlog from "~/components/ShareModal/apps/ShareBlog";
import { AccessControl } from '~/features/AccessControl/AccessControl';
import useActionBar from '~/features/ActionBar/useActionBar';
import { useShareResource, useUpdateResource } from '~/services/queries';
import { useSelectedResources } from '~/store';

const PublishModal = lazy(
  async () => await import('~/features/ActionBar/Publish/PublishModal'),
);

const UpdateModal = lazy(
  async () => await import('~/features/ActionBar/Resource/ResourceModal'),
);

const DeleteModal = lazy(async () => await import('./Delete/DeleteModal'));
const MoveModal = lazy(async () => await import('./Move/MoveModal'));
const FolderModal = lazy(async () => await import('./Folder/FolderModal'));
const ExportModal = lazy(async () => await import('./Export/ExportModal'));

export default function ActionBarContainer() {
  const { appCode } = useEdificeClient();

  const { t } = useTranslation();
  const {
    actions,
    selectedElement,
    isMoveModalOpen,
    isDeleteModalOpen,
    isPublishModalOpen,
    isActionBarOpen,
    isEditFolderOpen,
    overrideLabel,
    onEditFolderCancel,
    onEditFolderSuccess,
    isEditResourceOpen,
    onEditResourceCancel,
    onEditResourceSuccess,
    isShareResourceOpen,
    onShareResourceCancel,
    onShareResourceSuccess,
    isExportModalOpen,
    onExportCancel,
    onExportSuccess,
    onMoveCancel,
    onMoveSuccess,
    onDeleteCancel,
    onDeleteSuccess,
    onPublishCancel,
    onPublishSuccess,
    isActivable,
    handleClick,
  } = useActionBar();

  const selectedResources = useSelectedResources();
  const selectedResource = selectedResources[0];

  const shareResource = useShareResource(appCode);
  const updateResource = useUpdateResource(appCode);

  const transition = useTransition(isActionBarOpen, {
    from: { opacity: 0, transform: 'translateY(100%)' },
    enter: { opacity: 1, transform: 'translateY(0)' },
    leave: { opacity: 0, transform: 'translateY(100%)' },
  });

  return (
    <>
      {transition((style, isActionBarOpen) => {
        return (
          isActionBarOpen && (
            <animated.div
              className="position-fixed bottom-0 start-0 end-0"
              style={{ ...style, zIndex: 4 }}
            >
              <ActionBar>
                {actions
                  ?.filter(
                    (action: IAction) =>
                      action.available && action.target === 'actionbar',
                  )
                  .map((action: IAction) => {
                    return (
                      isActivable(action) && (
                        <AccessControl
                          key={action.id}
                          resourceRights={selectedElement}
                          roleExpected={action.right!}
                          action={action}
                        >
                          <Button
                            id={action.id}
                            key={action.id}
                            type="button"
                            color="primary"
                            variant="filled"
                            onClick={() => {
                              handleClick(action);
                            }}
                          >
                            {t(overrideLabel(action))}
                          </Button>
                        </AccessControl>
                      )
                    );
                  })}
              </ActionBar>
            </animated.div>
          )
        );
      })}

      <Suspense fallback={<LoadingScreen />}>
        {isMoveModalOpen && (
          <MoveModal
            isOpen={isMoveModalOpen}
            onCancel={onMoveCancel}
            onSuccess={onMoveSuccess}
          />
        )}
        {isDeleteModalOpen && (
          <DeleteModal
            isOpen={isDeleteModalOpen}
            onCancel={onDeleteCancel}
            onSuccess={onDeleteSuccess}
          />
        )}
        {isPublishModalOpen && selectedResource && (
          <PublishModal
            isOpen={isPublishModalOpen}
            resourceId={selectedResource.assetId}
            onCancel={onPublishCancel}
            onSuccess={onPublishSuccess}
          />
        )}
        {isEditFolderOpen && (
          <FolderModal
            edit={true}
            isOpen={isEditFolderOpen}
            onCancel={onEditFolderCancel}
            onSuccess={onEditFolderSuccess}
          />
        )}
        {isEditResourceOpen && selectedResource && (
          <UpdateModal
            mode="update"
            isOpen={isEditResourceOpen}
            resourceId={selectedResource.assetId}
            updateResource={updateResource}
            onCancel={onEditResourceCancel}
            onSuccess={onEditResourceSuccess}
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
          </UpdateModal>
        )}
        {isShareResourceOpen && selectedResource && (
          <ShareModal
            isOpen={isShareResourceOpen}
            shareResource={shareResource}
            shareOptions={{
              resourceCreatorId: selectedResource.creatorId,
              resourceId: selectedResource.assetId,
              resourceRights: selectedResource.rights,
            }}
            onCancel={onShareResourceCancel}
            onSuccess={onShareResourceSuccess}
          >
            {appCode === 'blog' ? (
              <ShareBlog
                resourceId={selectedResource.assetId}
                updateResource={updateResource}
              />
            ) : null}
          </ShareModal>
        )}
        {isExportModalOpen && selectedResource && (
          <ExportModal
            isOpen={isExportModalOpen}
            onCancel={onExportCancel}
            onSuccess={onExportSuccess}
          ></ExportModal>
        )}
      </Suspense>
    </>
  );
}

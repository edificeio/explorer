import { lazy, Suspense } from "react";

import { Button, ActionBar, LoadingScreen } from "@edifice-ui/react";
import { useTransition, animated } from "@react-spring/web";
import { type IAction } from "edifice-ts-client";
import { useTranslation } from "react-i18next";

import { AccessControl } from "~/features/AccessControl/AccessControl";
import useActionBar from "~/features/ActionBar/useActionBar";
import { useShareResource, useUpdateResource } from "~/services/queries";
import { useSelectedResources } from "~/store";

const ShareModal = lazy(
  async () => await import("~/features/ActionBar/Resource/ResourceModal"),
);

const PublishModal = lazy(
  async () => await import("~/features/ActionBar/Resource/ResourceModal"),
);

const UpdateModal = lazy(
  async () => await import("~/features/ActionBar/Resource/ResourceModal"),
);

const DeleteModal = lazy(async () => await import("./Delete/DeleteModal"));
const MoveModal = lazy(async () => await import("./Move/MoveModal"));
const FolderModal = lazy(async () => await import("./Folder/FolderModal"));

export default function ActionBarContainer() {
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
  const shareResource = useShareResource();
  const updateResource = useUpdateResource();

  const transition = useTransition(isActionBarOpen, {
    from: { opacity: 0, transform: "translateY(100%)" },
    enter: { opacity: 1, transform: "translateY(0)" },
    leave: { opacity: 0, transform: "translateY(100%)" },
  });

  return (
    <>
      {transition((style, isActionBarOpen) => {
        return (
          isActionBarOpen && (
            <animated.div
              className="position-fixed bottom-0 start-0 end-0 z-3"
              style={style}
            >
              <ActionBar>
                {actions
                  ?.filter(
                    (action: IAction) =>
                      action.available && action.target === "actionbar",
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
        {isPublishModalOpen && (
          <PublishModal
            isOpen={isPublishModalOpen}
            resource={selectedResources[0]}
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
        {isEditResourceOpen && (
          <UpdateModal
            mode="update"
            actions={actions}
            isOpen={isEditResourceOpen}
            selectedResource={selectedResources[0]}
            updateResource={updateResource}
            onCancel={onEditResourceCancel}
            onSuccess={onEditResourceSuccess}
          />
        )}
        {isShareResourceOpen && (
          <ShareModal
            isOpen={isShareResourceOpen}
            resource={selectedResources[0]}
            updateResource={updateResource}
            shareResource={shareResource}
            onCancel={onShareResourceCancel}
            onSuccess={onShareResourceSuccess}
          />
        )}
      </Suspense>
    </>
  );
}

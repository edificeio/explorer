import { lazy, Suspense } from "react";

import { Button, ActionBar, LoadingScreen } from "@ode-react-ui/components";
import { useI18n } from "@ode-react-ui/core";
import { useTransition, animated } from "@react-spring/web";
import { type IAction } from "ode-ts-client";

import useActionBar from "~features/Actionbar/hooks/useActionBar";
import { AccessControl } from "~shared/components/AccessControl";

const ShareResourceModal = lazy(
  async () => await import("./ShareResourceModal"),
);
const DeleteModal = lazy(async () => await import("./DeleteModal"));
const MoveModal = lazy(async () => await import("./MoveModal"));
const EditFolderModal = lazy(async () => await import("./EditFolderModal"));
const EditResourceModal = lazy(async () => await import("./EditResourceModal"));
const PublishModal = lazy(async () => await import("./PublishModal"));

export default function ActionBarContainer() {
  const { i18n } = useI18n();
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
                            {i18n(overrideLabel(action))}
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
            onCancel={onPublishCancel}
            onSuccess={onPublishSuccess}
          />
        )}
        {isEditFolderOpen && (
          <EditFolderModal
            edit={true}
            isOpen={isEditFolderOpen}
            onCancel={onEditFolderCancel}
            onSuccess={onEditFolderSuccess}
          />
        )}
        {isEditResourceOpen && (
          <EditResourceModal
            edit={true}
            isOpen={isEditResourceOpen}
            onCancel={onEditResourceCancel}
            onSuccess={onEditResourceSuccess}
          />
        )}
        {isShareResourceOpen && (
          <ShareResourceModal
            isOpen={isShareResourceOpen}
            onCancel={onShareResourceCancel}
            onSuccess={onShareResourceSuccess}
          />
        )}
      </Suspense>
    </>
  );
}

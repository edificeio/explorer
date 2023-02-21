import { lazy, Suspense } from "react";

import useActionBar from "@features/Actionbar/hooks/useActionBar";
import {
  Button,
  ActionBar,
  useOdeClient,
  LoadingScreen,
} from "@ode-react-ui/core";
import { AnimatePresence, motion } from "framer-motion";
import { type IAction } from "ode-ts-client";

const DeleteModal = lazy(
  async () => await import("@features/Actionbar/components/DeleteModal"),
);
const MoveModal = lazy(
  async () => await import("@features/Actionbar/components/MoveModal"),
);
const EditFolderModal = lazy(async () => await import("./EditFolderModal"));
const EditResourceModal = lazy(async () => await import("./EditResourceModal"));
const PublishModal = lazy(async () => await import("./PublishModal"));

export default function ActionBarContainer() {
  const { i18n } = useOdeClient();
  const {
    actions,
    isMoveModalOpen,
    isDeleteModalOpen,
    isPublishModalOpen,
    isActionBarOpen,
    isEditFolderOpen,
    onEditFolderCancel,
    onEditFolderSuccess,
    isEditResourceOpen,
    onEditResourceCancel,
    onEditResourceSuccess,
    onMoveCancel,
    onMoveSuccess,
    onDeleteCancel,
    onDeleteSuccess,
    onPublishCancel,
    onPublishSuccess,
    isActivable,
    handleClick,
  } = useActionBar();

  return (
    <>
      <AnimatePresence>
        {isActionBarOpen ? (
          <motion.div
            className="position-fixed bottom-0 start-0 end-0 z-3"
            initial={{ y: "100%" }}
            animate={{ y: 0 }}
            exit={{ y: "100%" }}
            transition={{
              y: { duration: 0.5 },
              default: { ease: "linear" },
            }}
          >
            <ActionBar>
              {actions
                .filter(
                  (action: IAction) =>
                    action.available && action.target === "actionbar",
                )
                .map((action: IAction) => {
                  return (
                    isActivable(action) && (
                      <Button
                        key={action.id}
                        type="button"
                        color="primary"
                        variant="filled"
                        onClick={() => {
                          handleClick(action);
                        }}
                      >
                        {i18n(`explorer.actions.${action.id}`)}
                      </Button>
                    )
                  );
                })}
            </ActionBar>
          </motion.div>
        ) : null}
      </AnimatePresence>
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
      </Suspense>
    </>
  );
}

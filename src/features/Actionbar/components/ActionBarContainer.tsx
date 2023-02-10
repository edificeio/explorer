import DeleteModal from "@features/Actionbar/components/DeleteModal";
import MoveModal from "@features/Actionbar/components/MoveModal";
import useActionBar from "@features/Actionbar/hooks/useActionBar";
import { Button, ActionBar, useOdeClient } from "@ode-react-ui/core";
// import { useClickOutside } from "@ode-react-ui/hooks";
import { type IAction } from "ode-ts-client";

import EditFolderModal from "./EditFolderModal";
import EditResourceModal from "./EditResourceModal";
import PublishModal from "./PublishModal";

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
    // onClearActionBar,
  } = useActionBar();

  // * Unable to use or multi-selection not working
  // const ref = useClickOutside(onClearActionBar);

  return isActionBarOpen ? (
    <div className="position-fixed bottom-0 start-0 end-0">
      <ActionBar
      // ref={ref}
      >
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
      <MoveModal
        isOpen={isMoveModalOpen}
        onCancel={onMoveCancel}
        onSuccess={onMoveSuccess}
      />
      <DeleteModal
        isOpen={isDeleteModalOpen}
        onCancel={onDeleteCancel}
        onSuccess={onDeleteSuccess}
      />
      <PublishModal
        isOpen={isPublishModalOpen}
        onCancel={onPublishCancel}
        onSuccess={onPublishSuccess}
      />
      <EditFolderModal
        edit={true}
        isOpen={isEditFolderOpen}
        onCancel={onEditFolderCancel}
        onSuccess={onEditFolderSuccess}
      />
      <EditResourceModal
        edit={true}
        isOpen={isEditResourceOpen}
        onCancel={onEditResourceCancel}
        onSuccess={onEditResourceSuccess}
      />
    </div>
  ) : null;
}

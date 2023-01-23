import { useExplorerContext } from "@contexts/index";
import DeleteModal from "@features/Actionbar/components/DeleteModal";
import MoveModal from "@features/Actionbar/components/MoveModal";
import useActionBar from "@features/Actionbar/hooks/useActionBar";
import { Button, ActionBar } from "@ode-react-ui/core";
import { IAction } from "ode-ts-client";

export default function ActionBarContainer() {
  const { i18n } = useExplorerContext();
  const {
    actions,
    isMoveModalOpen,
    isDeleteModalOpen,
    isActionBarOpen,
    onMoveCancel,
    onMoveSuccess,
    onDeleteCancel,
    onDeleteSuccess,
    isActivable,
    handleClick,
  } = useActionBar();

  return isActionBarOpen ? (
    <div className="position-fixed bottom-0 start-0 end-0">
      <ActionBar>
        {actions
          .filter(
            (action: IAction) =>
              action.available && action.target === "actionbar",
          )
          .map((action: IAction) => {
            console.log("action av", action.target);
            return (
              isActivable(action) && (
                <Button
                  key={action.id}
                  type="button"
                  color="primary"
                  variant="filled"
                  onClick={() => handleClick(action)}
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
    </div>
  ) : null;
}

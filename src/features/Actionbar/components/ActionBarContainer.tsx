import useActionBar from "@features/Actionbar/hooks/useActionBar";
import { Button, ActionBar } from "@ode-react-ui/core";
import { IAction } from "ode-ts-client";

export default function ActionBarContainer({ isOpen }: { isOpen: boolean }) {
  const { actions, isActivable, handleClick } = useActionBar();

  return isOpen ? (
    <div className="position-fixed bottom-0 start-0 end-0">
      <ActionBar>
        {actions
          .filter((action) => action.available)
          .map((action: IAction) => (
            <Button
              key={action.id}
              type="button"
              color="primary"
              variant="filled"
              disabled={!isActivable(action)}
              onClick={() => handleClick(action)}
            >
              {action.id}
            </Button>
          ))}
      </ActionBar>
    </div>
  ) : null;
}

import { type ReactNode, type ReactElement } from "react";

import { Button } from "@ode-react-ui/components";
import { useI18n } from "@ode-react-ui/core";
import { IAction, RightRole } from "ode-ts-client";

import useActionBar from "~/features/Actionbar/hooks/useActionBar";
import useAccessControl, {
  type IObjectWithRights,
} from "~/shared/hooks/useAccessControl";
import { useIsTrash, useStoreActions } from "~/store";

// TODO move it to ode-react-ui with useAccessControl

interface AccessControlProps {
  roleExpected: RightRole | RightRole[];
  resourceRights: string | string[] | IObjectWithRights | IObjectWithRights[];
  action: IAction;
  children: ReactNode;
  renderWhenForbidden?: () => ReactElement;
}

export function AccessControl({
  resourceRights,
  roleExpected,
  action,
  children,
  renderWhenForbidden,
}: AccessControlProps): ReactElement {
  const { i18n } = useI18n();
  const { overrideLabel } = useActionBar();
  const { visible } = useAccessControl({
    roles: roleExpected,
    rights: resourceRights,
    action: action?.id,
  });
  const { setResourceActionDisable } = useStoreActions();
  const isTrashFolder = useIsTrash();
  if (visible) {
    return <>{children}</>;
  } else if (renderWhenForbidden) {
    return renderWhenForbidden();
  } else if (isTrashFolder && !visible) {
    return (
      <Button
        type="button"
        color="primary"
        variant="filled"
        onClick={() => {
          setResourceActionDisable(true);
        }}
      >
        {i18n(overrideLabel(action))}
      </Button>
    );
  } else {
    return <></>;
  }
}

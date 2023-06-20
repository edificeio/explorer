import { type ReactNode, type ReactElement } from "react";

import { type RightRole } from "ode-ts-client/dist/services";

import useAccessControl, {
  type IObjectWithRights,
} from "~/shared/hooks/useAccessControl";

// TODO move it to ode-react-ui with useAccessControl

interface AccessControlProps {
  roleExpected: RightRole | RightRole[];
  resourceRights: string | string[] | IObjectWithRights | IObjectWithRights[];
  actionId: string | undefined;
  children: ReactNode;
  renderWhenForbidden?: () => ReactElement;
}

export function AccessControl({
  resourceRights,
  roleExpected,
  actionId,
  children,
  renderWhenForbidden,
}: AccessControlProps): ReactElement {
  const { visible } = useAccessControl({
    roles: roleExpected,
    rights: resourceRights,
    action: actionId,
  });
  if (visible) {
    return <>{children}</>;
  } else if (renderWhenForbidden) {
    return renderWhenForbidden();
  } else {
    return <></>;
  }
}

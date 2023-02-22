import { type ReactNode, type ReactElement } from "react";

import useAccessControl from "@shared/hooks/useAccessControl";
import { type IResource } from "ode-ts-client";
import { type RightRole } from "ode-ts-client/dist/services";

// TODO move it to ode-react-ui with useAccessControl

interface AccessControlProps {
  roleExpected: RightRole | RightRole[];
  resourceRights: string | string[] | IResource | IResource[];
  children: ReactNode;
  renderWhenForbidden?: () => ReactElement;
}

export function AccessControl({
  resourceRights,
  roleExpected,
  children,
  renderWhenForbidden,
}: AccessControlProps): ReactElement {
  const { visible } = useAccessControl({
    roles: roleExpected,
    rights: resourceRights,
  });
  if (visible) {
    return <>{children}</>;
  } else if (renderWhenForbidden) {
    return renderWhenForbidden();
  } else {
    return <></>;
  }
}

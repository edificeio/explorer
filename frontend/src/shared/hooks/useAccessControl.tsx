import { useEffect, useState } from "react";

import { type RightRole, odeServices } from "ode-ts-client";
// TODO move it to ode-react-ui with AccessControl

export interface IObjectWithRights {
  rights: string[];
}

interface AccessControlProps {
  roles: RightRole | RightRole[];
  rights: string | string[] | IObjectWithRights | IObjectWithRights[];
}

export default function useAccessControl({
  roles,
  rights,
}: AccessControlProps) {
  const [visible, setVisible] = useState<boolean>(false);
  // run effect if params changes
  useEffect(() => {
    refreshState();
  }, [roles, rights]);

  const checkRights = async function (rights: string[] | string) {
    const safeRight = rights instanceof Array ? rights : [rights];
    if (roles instanceof Array) {
      // roles is of type RightRole[]
      const can = await odeServices
        .rights()
        .sessionHasAtLeastOneResourceRight(roles, safeRight);
      setVisible(can);
    } else {
      // roles is of type RightRole
      const can = await odeServices
        .rights()
        .sessionHasResourceRight(roles, safeRight);
      setVisible(can);
    }
  };

  const checkRightForMultipleResources = async function (rights: string[][]) {
    if (roles instanceof Array) {
      const can = await odeServices
        .rights()
        .sessionHasAtLeastOneResourceRightForEachList(roles, rights);
      setVisible(can);
    } else {
      const can = await odeServices
        .rights()
        .sessionHasResourceRightForEachList(roles, rights);
      setVisible(can);
    }
  };

  // compute visibility according to rights
  const refreshState = async function () {
    if (roles === undefined) {
      setVisible(true);
      return;
    }

    if (rights instanceof Array) {
      // rights are of type Array
      if (rights.length > 0) {
        if (typeof rights[0] === "string") {
          // rights are of type string[]
          await checkRights(rights as string[]);
        } else {
          // rights are of type IResource[]
          const rightsArray = (rights as IObjectWithRights[]).map(
            (e) => e.rights,
          );
          await checkRightForMultipleResources(rightsArray);
        }
      } else {
        // array of rights is empty
        setVisible(false);
      }
    } else {
      // rights are not of type Array
      if (typeof rights === "string") {
        await checkRights(rights);
      } else {
        // rights are of type IResource
        await checkRights(rights.rights);
      }
    }
  };
  return {
    visible,
    refreshState,
  };
}

import { type IResource } from "ode-ts-client";

export function isResourceShared(resource: IResource) {
  const { rights, creatorId } = resource;
  const filteredRights = rights.filter((right) => !right.includes(creatorId));

  const isShared =
    filteredRights.length > 1 && !filteredRights.includes(creatorId);

  return isShared;
}

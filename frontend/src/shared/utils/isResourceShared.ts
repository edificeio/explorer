import { type IResource } from "ode-ts-client";

export function isResourceShared(resource: IResource) {
  const { rights, creatorId } = resource;
  const filteredRights = rights.filter((right) => !right.includes(creatorId));

  console.log(filteredRights.length);

  return filteredRights.length >= 1;
}

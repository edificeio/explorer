import { type IResource } from '@edifice.io/client';

export function isResourceShared(resource: IResource) {
  const { rights, creatorId } = resource;
  const filteredRights = rights.filter((right) => !right.includes(creatorId));

  return filteredRights.length >= 1;
}

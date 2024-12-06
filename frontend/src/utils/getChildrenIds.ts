import { FOLDER } from '@edifice.io/client';
import { TreeData, findNodeById } from '@edifice.io/react';

export function getChildrenIds(data: TreeData, folderId: string): string[] {
  const findItem = findNodeById(data, folderId);
  if (findItem?.folder?.childrenIds) {
    const nodes = findItem?.folder.childrenIds || [];
    return [...nodes, folderId];
  } else if (folderId === FOLDER.BIN) {
    return [FOLDER.BIN];
  } else {
    return [FOLDER.DEFAULT];
  }
}

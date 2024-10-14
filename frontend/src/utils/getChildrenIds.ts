import { TreeData, findNodeById } from '@edifice-ui/react';
import { FOLDER } from 'edifice-ts-client';

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

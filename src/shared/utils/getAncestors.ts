import { TreeNode } from "@ode-react-ui/core";
import { FOLDER } from "ode-ts-client";

import { findNodeById } from "./findNodeById";

export function getAncestors(folderId: string, treeData: TreeNode): string[] {
  const findItem = findNodeById(folderId, treeData);
  if (findItem?.folder?.ancestors) {
    const nodes = findItem?.folder.ancestors || [];
    return [...nodes, folderId];
  } else if (folderId === FOLDER.BIN) {
    return [FOLDER.BIN];
  } else {
    return [FOLDER.DEFAULT];
  }
}
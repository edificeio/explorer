import { type TreeData } from "@edifice-ui/react";

import { modifyNode } from "./modifyNode";

export function deleteNode(
  treeData: TreeData,
  { folders }: { folders: string[] },
): TreeData {
  return modifyNode(treeData, (node) => {
    if (folders.includes(node.id)) {
      return undefined;
    } else {
      return node;
    }
  });
}

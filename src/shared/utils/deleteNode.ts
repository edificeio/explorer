import { TreeNode } from "@ode-react-ui/core";

import { modifyNode } from "./modifyNode";

export function deleteNode(
  treeData: TreeNode,
  { folders }: { folders: string[] },
): TreeNode {
  return modifyNode(treeData, (node, parent) => {
    if (folders.includes(node.id)) {
      return undefined;
    } else {
      return node;
    }
  });
}

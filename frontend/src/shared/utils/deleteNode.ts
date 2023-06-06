import { type TreeNode } from "@ode-react-ui/components";

import { modifyNode } from "./modifyNode";

export function deleteNode(
  treeData: TreeNode,
  { folders }: { folders: string[] },
): TreeNode {
  return modifyNode(treeData, (node) => {
    if (folders.includes(node.id)) {
      return undefined;
    } else {
      return node;
    }
  });
}

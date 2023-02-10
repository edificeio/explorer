import { type TreeNode } from "@ode-react-ui/advanced";

import { findNodeById } from "./findNodeById";
import { modifyNode } from "./modifyNode";

export function moveNode(
  treeData: TreeNode,
  { destinationId, folders }: { destinationId: string; folders: string[] },
): TreeNode {
  return modifyNode(treeData, (node, parent) => {
    if (destinationId === node.id) {
      // add to new position
      const newChildren = [...(node.children || [])];
      const childrenIds = node.children?.map((e) => e.id) || [];
      for (const folder of folders) {
        // if not in children yet => move on it
        if (!childrenIds.includes(folder)) {
          const item = findNodeById(folder, treeData);
          item && newChildren.push(item);
        }
      }
      const newNode: TreeNode = {
        ...node,
        children: newChildren,
      };
      return newNode;
    } else if (folders.includes(node.id) && destinationId !== parent?.id) {
      // delete from original position
      return undefined;
    } else {
      return node;
    }
  });
}

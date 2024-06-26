import { type TreeNode } from "@edifice-ui/react";

import type TreeNodeFolderWrapper from "~/utils/TreeNodeFolderWrapper";
import { arrayUnique } from "./arrayUnique";
import { findNodeById } from "./findNodeById";
import { modifyNode } from "./modifyNode";

export function moveNode(
  treeData: TreeNode,
  { destinationId, folders }: { destinationId: string; folders: string[] },
): TreeNode {
  return modifyNode(treeData, (node, parent) => {
    if (destinationId === node.id) {
      const parentAncestors = [
        ...((node as TreeNodeFolderWrapper).folder?.ancestors || []),
      ];
      const ancestors = arrayUnique([...parentAncestors, node.id]);
      // add to new position
      const newChildren = [...(node.children || [])];
      const childrenIds = node.children?.map((child) => child.id) || [];
      for (const folder of folders) {
        // if not in children yet => move on it
        if (!childrenIds.includes(folder)) {
          const item = findNodeById(treeData, folder);

          item &&
            newChildren.push({
              ...item,
              folder: {
                ...item?.folder,
                ancestors,
              },
            });
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

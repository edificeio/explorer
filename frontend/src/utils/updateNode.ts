import { type TreeData } from "@edifice-ui/react";
import { type IFolder } from "edifice-ts-client";

import { modifyNode } from "./modifyNode";
import TreeNodeFolderWrapper from "./TreeNodeFolderWrapper";

export function updateNode(
  treeData: TreeData,
  { folderId, newFolder }: { folderId: string; newFolder: IFolder },
): TreeData {
  return modifyNode(treeData, (node) => {
    if (node.id === folderId) {
      return new TreeNodeFolderWrapper(newFolder);
    } else {
      return node;
    }
  });
}

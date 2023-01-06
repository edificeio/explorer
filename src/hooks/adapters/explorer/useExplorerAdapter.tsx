import { useEffect, useState } from "react";

import { useExplorerContext } from "@contexts/ExplorerContext";
import { TreeNode } from "@ode-react-ui/core";
import { IFolder, IResource } from "ode-ts-client";

// import ResourceCardWrapper from "./ResourceCardWrapper";
import TreeNodeFolderWrapper from "./TreeNodeFolderWrapper";

/**
 * This hook acts as a data-model adapter.
 * It allows a TreeView component to explore and display folders streamed from an IExplorerContext.
 */
export default function useExplorerAdapter() {
  const { context } = useExplorerContext();

  const [treeData, setTreeData] = useState<TreeNode>({
    id: "default",
    name: "Blogs",
    section: true,
    children: [],
  });

  const [listData, setListData] = useState<IResource[]>([]);

  // TODO  const selectedNode = treeData;

  function findNodeById(id: string, data: TreeNode): TreeNode | undefined {
    let res: TreeNode | undefined;
    if (data?.id === id) {
      return data;
    }
    if (data?.children?.length) {
      data?.children?.every((childNode) => {
        res = findNodeById(id, childNode);
        return res === undefined; // break loop if res is found
      });
    }
    return res;
  }

  function wrapTreeData(folders?: IFolder[]) {
    folders?.forEach((folder) => {
      const parentFolder = findNodeById(folder.parentId, treeData);
      if (!parentFolder?.children?.find((child) => child.id === folder.id)) {
        if (parentFolder && parentFolder.children) {
          parentFolder.children = [
            ...parentFolder.children,
            new TreeNodeFolderWrapper(folder),
          ];
        }
      }
    });

    setTreeData({ ...treeData });
  }

  function wrapResourceData(resources?: IResource[]) {
    if (resources && resources.length) {
      setListData((d) => d.concat(resources));
    }
  }

  // Observe streamed search results
  useEffect(() => {
    const subscription = context.latestResources().subscribe({
      next: (resultset: any) => {
        wrapTreeData(resultset?.output?.folders);
        wrapResourceData(resultset?.output?.resources);
      },
    });

    return () => {
      if (subscription) {
        subscription.unsubscribe();
      }
    };
  }, []); // execute effect only once

  return {
    treeData,
    listData,
  };
}

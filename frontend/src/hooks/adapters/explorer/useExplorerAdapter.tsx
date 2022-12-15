import { useEffect, useState } from "react";

import { useExplorerContext } from "@contexts/ExplorerContext";
import { IFolder, IResource } from "ode-ts-client";

import ResourceCardWrapper from "./ResourceCardWrapper";
import TreeNodeFolderWrapper from "./TreeNodeFolderWrapper";
import { Card, TreeNode } from "./types";

/**
 * This hook acts as a data-model adapter.
 * It allows a TreeView component to explore and display folders streamed from an IExplorerContext.
 */
export default function useExplorerAdapter() {
  const { context } = useExplorerContext();

  const [treeData, setTreeData] = useState<TreeNode>({
    id: "root",
    name: "Blogs",
    section: true,
    children: [],
  });

  const [listData, setListData] = useState<Array<Card>>([]);

  // TODO  const selectedNode = treeData;

  function wrapTreeData(folders?: IFolder[]) {
    if (treeData && treeData.children && folders && folders.length) {
      treeData.children.push(
        ...folders.map((f) => new TreeNodeFolderWrapper(f)),
      );
      setTreeData(treeData); // ça ressemble à un hack :)
    }
  }

  function wrapResourceData(resources?: IResource[]) {
    if (resources && resources.length) {
      setListData((d) =>
        d.concat(resources.map((r) => new ResourceCardWrapper(r))),
      );
    }
  }

  // Observe streamed search results
  useEffect(() => {
    const subscription = context.latestResources().subscribe({
      next: (resultset) => {
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

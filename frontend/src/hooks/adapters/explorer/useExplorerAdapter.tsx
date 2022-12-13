import { useEffect, useState } from "react";

import { IExplorerContext, IFolder, IResource } from "ode-ts-client";

import ResourceCardWrapper from "./ResourceCardWrapper";
import TreeNodeFolderWrapper from "./TreeNodeFolderWrapper";
import { Card, TreeNode } from "./types";

/**
 * This hook acts as a data-model adapter.
 * It allows a TreeView component to explore and display folders streamed from an IExplorerContext.
 */
export default function useExplorerAdapter(explorerContext: IExplorerContext) {
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
    if (listData && resources && resources.length) {
      setListData(
        listData.concat(resources.map((r) => new ResourceCardWrapper(r))),
      );
    }
  }

  // Observe streamed search results
  useEffect(() => {
    const subscription = explorerContext.latestResources().subscribe({
      next: (resultset) => {
        if (resultset) {
          // Prepare next page search
          const { pagination } = explorerContext.getSearchParameters();
          pagination.startIdx =
            resultset.input.pagination.startIdx +
            resultset.output.resources.length;
          if (
            typeof pagination.maxIdx !== "undefined" &&
            pagination.startIdx > pagination.maxIdx
          ) {
            pagination.startIdx = pagination.maxIdx;
          }
        }
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

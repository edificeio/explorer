import { useEffect, useState } from "react";

import { IExplorerContext, IFolder } from "ode-ts-client";

/* TODO exporter TreeNode depuis ode-react-ui/advanced */
export interface TreeNode {
  id: string;
  name: string;
  section?: boolean;
  children?: TreeNode[];
}

/** Utility inner class that wraps an IFolder into a TreeNode. */
class TreeNodeFolderWrapper implements TreeNode {
  constructor(private folder: IFolder) {}

  public section = false;

  public readonly children: Array<TreeNode> = [];

  get id() {
    return this.folder.id;
  }

  get name() {
    return this.folder.name;
  }

  get childNumber() {
    return this.folder.childNumber;
  }
}

/**
 * This hook acts as a data-model adapter.
 * It allows a TreeView component to explore and display folders streamed from an IExplorerContext.
 */
export default function useExplorerTreeView(explorerContext: IExplorerContext) {
  const [treeData, setTreeData] = useState<TreeNode>({
    id: "root",
    name: "Blogs",
    section: true,
    children: [],
  });

  // TODO  const selectedNode = treeData;

  function wrapTreeData(folders?: IFolder[]) {
    if (treeData && treeData.children && folders && folders.length) {
      treeData.children.push(
        ...folders.map((f) => new TreeNodeFolderWrapper(f)),
      );
    }
  }

  // Observe streamed search results
  useEffect(() => {
    const subscription = explorerContext.latestResources().subscribe({
      next: (resultset) => {
        wrapTreeData(resultset?.output?.folders);
        setTreeData(treeData);
      },
    });

    return () => {
      if (subscription) {
        subscription.unsubscribe();
      }
    };
  }, []);

  return {
    treeData,
  };
}

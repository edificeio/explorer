import { IFolder } from "ode-ts-client";

import { TreeNode } from "../types";

/** Utility inner class that wraps an IFolder into a TreeNode. */
export default class TreeNodeFolderWrapper implements TreeNode {
  public readonly id: string;
  public readonly name: string;
  public readonly childNumber: number;
  constructor(public readonly folder: IFolder) {
    this.id = folder.id;
    this.name = folder.name;
    this.childNumber = folder.childNumber;
  }

  public section = false;

  public readonly children: TreeNode[] = [];
}

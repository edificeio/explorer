import { IFolder } from "ode-ts-client";

import { TreeNode } from "../types";

/** Utility inner class that wraps an IFolder into a TreeNode. */
export default class TreeNodeFolderWrapper implements TreeNode {
  constructor(private readonly folder: IFolder) {}

  public section = false;

  public readonly children: TreeNode[] = [];

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

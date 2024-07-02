import { type TreeData } from "@edifice-ui/react";
import { type IFolder } from "edifice-ts-client";

/** Utility inner class that wraps an IFolder into a TreeData. */
export default class TreeNodeFolderWrapper implements TreeData {
  constructor(public readonly folder: IFolder) {
    this.id = folder.id;
    this.name = folder.name;
    this.childNumber = folder.childNumber;
  }

  public readonly id: string;
  public readonly name: string;
  public readonly childNumber: number;

  public section = false;

  public readonly children: TreeData[] = [];
}

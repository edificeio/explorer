export interface TreeNode {
  /**
   * @param id : node's id
   */
  id: string;

  /**
   * @param name : name's id
   */
  name: string;

  /**
   * @param section: indicate if node is a top section (useful for specific icon)
   */
  section?: boolean;
  /**
   * @param selected: if first node is a section, it is selected by default
   */
  selected?: boolean;

  /**
   * Is this node contains children ?
   */
  children?: readonly TreeNode[];
  /**
   * All none declare types
   */
  [key: string]: any;
}

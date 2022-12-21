/* TODO exporter TreeNode depuis ode-react-ui/advanced */
export interface TreeNode {
  id: string;
  name: string;
  section?: boolean;
  children?: TreeNode[];
}

export interface Card {
  id: string;
  name: string;
  thumbnail: string;
}

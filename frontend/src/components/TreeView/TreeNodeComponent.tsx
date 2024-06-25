import React from "react";

import TreeItem from "./TreeItem";
import { TreeNode } from "./TreeNode";

interface TreeNodeComponentProps {
  node: TreeNode;
  handlers?: {
    unselectAll: () => void;
    select: (nodeId: string) => void;
  };
  selectedItem: string | null;
  selectedNodesIds?: string[];
  elementDragOver?: {
    isOver: boolean;
    overId: string | undefined;
  };
  handleItemFold: (nodeId: string) => void;
  handleItemSelect: (nodeId: string) => void;
  handleItemUnfold: (nodeId: string) => void;
  handleItemFocus: (nodeId: string) => void;
  handleItemBlur: (nodeId: string) => void;
}

const TreeNodeComponent: React.FC<TreeNodeComponentProps> = ({
  node,
  handlers,
  selectedItem,
  selectedNodesIds,
  elementDragOver,
  handleItemSelect,
  handleItemFold,
  handleItemUnfold,
  handleItemFocus,
  handleItemBlur,
}) => {
  return (
    <TreeItem
      key={node.id}
      nodeId={node.id}
      label={node.name}
      section={node.section}
      // selectedNodesIds={selectedNodesIds}
      selected={selectedItem === node.id}
      onItemSelect={handleItemSelect}
      onItemFold={handleItemFold}
      onItemUnfold={handleItemUnfold}
      onItemFocus={handleItemFocus}
      onItemBlur={handleItemBlur}
      elementDragOver={elementDragOver}
    >
      {Array.isArray(node.children)
        ? node.children.map((item) => (
            <TreeNodeComponent
              key={item.id}
              node={item}
              // handlers={handlers}
              selectedItem={selectedItem}
              // selectedNodesIds={selectedNodesIds}
              handleItemSelect={handleItemSelect}
              handleItemFold={handleItemFold}
              handleItemUnfold={handleItemUnfold}
              handleItemFocus={handleItemFocus}
              handleItemBlur={handleItemBlur}
              elementDragOver={elementDragOver}
            />
          ))
        : null}
    </TreeItem>
  );
};

export default TreeNodeComponent;

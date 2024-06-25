import { Ref, forwardRef, useEffect, useState } from "react";

import { TreeNode } from "./TreeNode";
import TreeNodeComponent from "./TreeNodeComponent";

export interface TreeViewHandlers {
  unselectAll: () => void;
  select: (nodeId: string) => void;
}

export interface TreeViewProps {
  /**
   * TreeNode data
   */
  data: TreeNode;

  /**
   * Node ID used for navigation folders
   */
  selectedNodesIds?: string[];

  /**
   * Sate element who is drag
   */
  elementDragOver?: {
    isOver: boolean;
    overId: string | undefined;
  };

  /**
   * Callback function to provide selected item to parent component
   */
  onTreeItemSelect?: (nodeId: string) => void;

  /**
   * Callback function to provide folded item to parent component
   */
  onTreeItemFold?: (nodeId: string) => void;

  /**
   * Callback function to provide unfolded item to parent component
   */
  onTreeItemUnfold?: (nodeId: string) => void;

  /**
   * Callback function to provide focused item to parent component
   */
  onTreeItemFocus?: (nodeId: string) => void;

  /**
   * Callback function to provide blured item to parent component
   */
  onTreeItemBlur?: (nodeId: string) => void;
}

/**
 * UI TreeView Component
 */

const TreeView = forwardRef(
  (props: TreeViewProps, ref: Ref<HTMLDivElement>) => {
    const {
      data,
      onTreeItemSelect,
      onTreeItemFold,
      onTreeItemUnfold,
      onTreeItemFocus,
      onTreeItemBlur,
      selectedNodesIds,
      elementDragOver,
    } = props;

    const [selectedItem, setSelectedItem] = useState<string | null>(null);

    useEffect(() => {
      if (selectedNodesIds) {
        handleSelectedNodesIds(selectedNodesIds[selectedNodesIds.length - 1]);
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedNodesIds]);

    /* useEffect(() => {
      if (selectedNodesIds?.length && selectedNodesIds?.length >= 1) {
        setSelectedItem(selectedNodesIds[selectedNodesIds.length - 1]);
      } else {
        setSelectedItem(null);
      }
    }, [selectedNodesIds]); */

    /* useEffect(() => {
      if (data.id === "default") setSelectedItem("default");
      console.log(data);
    }, [data]); */

    /* const handlers: TreeViewHandlers = useMemo(
      () => ({
        unselectAll() {
          setSelectedItem(null);
        },
        select(nodeId: string) {
          setSelectedItem(nodeId);
          onTreeItemSelect?.(nodeId);
        },
      }),
      [onTreeItemSelect],
    ); */

    // useImperativeHandle(ref, () => handlers, [handlers]);

    const handleSelectedNodesIds = (nodeId: string) => {
      onTreeItemUnfold?.(nodeId);
      onTreeItemSelect?.(nodeId);
      setSelectedItem(nodeId);
    };

    const handleItemFold = (nodeId: string) => {
      onTreeItemFold?.(nodeId);
    };

    const handleItemUnfold = (nodeId: string) => {
      onTreeItemUnfold?.(nodeId);
    };

    const handleItemSelect = (nodeId: string) => {
      console.log("handleItemSelect", nodeId);
      onTreeItemSelect?.(nodeId);
      setSelectedItem(nodeId);
    };

    const handleItemFocus = (nodeId: string) => {
      onTreeItemFocus?.(nodeId);
    };

    const handleItemBlur = (nodeId: string) => {
      onTreeItemBlur?.(nodeId);
    };

    console.log({ selectedItem });

    return (
      <div className="treeview" ref={ref}>
        <TreeNodeComponent
          node={data}
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
      </div>
    );
  },
);

TreeView.displayName = "TreeView";

export default TreeView;

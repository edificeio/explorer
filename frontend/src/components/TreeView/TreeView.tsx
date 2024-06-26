import {
  Ref,
  forwardRef,
  useEffect,
  useId,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from "react";

import { useDroppable } from "@dnd-kit/core";
import { Folder, RafterDown, RafterRight } from "@edifice-ui/icons";
import { TreeViewHandlers } from "@edifice-ui/react";
import clsx from "clsx";
import { FOLDER } from "edifice-ts-client";
import { useTranslation } from "react-i18next";
// import TreeNode from "./TreeNode";
// import { TreeNodeData } from "./types";

interface TreeNodeData {
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
  children?: readonly TreeNodeData[];
  /**
   * All none declare types
   */
  [key: string]: any;
}

interface TreeItemProps {
  /**
   * Node's id
   */
  nodeId: string;

  /**
   * Node's label
   */
  label: string;

  /**
   * ReactNode children
   */
  children: React.ReactNode;

  /**
   * Is current node a section (root element)
   */
  section?: boolean;

  /**
   * Is node selected
   */
  selected: boolean;

  /**
   * Is node expanded
   */
  expanded: boolean;

  /**
   * Is node over
   */
  focused: boolean;

  /**
   * Callback function to provide selected item to parent component (TreeView)
   */
  onItemDrag?: (nodeId: string) => void;

  /**
   * Callback function to provide selected item to parent component (TreeView)
   */
  onItemClick?: (nodeId: string) => void;

  /**
   * Callback function to provide unfolded item to parent component (TreeView)
   */
  onItemUnfold?: (nodeId: string) => void;

  /**
   * Sate element who is drag
   */
  elementDragOver?: {
    isOver: boolean;
    overId: string | undefined;
  };
}

const TreeItem = (props: TreeItemProps) => {
  const {
    nodeId,
    label,
    children,
    section,
    selected,
    expanded,
    focused,
    onItemClick,
  } = props;

  const { t } = useTranslation();

  const { setNodeRef } = useDroppable({
    id: useId(),
    data: {
      id: nodeId,
      name: label,
      folderTreeview: true,
      accepts: ["folder", "resource"],
    },
  });

  const rafterSize = section ? 16 : 12;

  const handleItemKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.code === "Enter" || event.code === "Space") {
      event.preventDefault();
      event.stopPropagation();

      onItemClick?.(nodeId);
    }
  };

  const handleItemClick = () => onItemClick?.(nodeId);

  const treeItemClasses = {
    action: clsx("action-container d-flex align-items-center gap-8 px-2", {
      "drag-focus": focused,
    }),
    arrow: clsx("py-8", {
      invisible: !Array.isArray(children),
    }),
    button: clsx(
      "flex-fill d-flex align-items-center text-truncate gap-8 py-8",
    ),
  };

  const showExpandedNodeChildren =
    Array.isArray(children) && !!children.length && expanded;

  const renderSection = () => (
    <ul role="tree" className="m-0 p-0">
      {renderItem()}
    </ul>
  );
  const renderItem = () => (
    <li
      key={nodeId}
      ref={setNodeRef}
      id={`listitem_${nodeId}`}
      role="treeitem"
      aria-selected={selected}
      aria-expanded={expanded}
    >
      <div>
        <div className={treeItemClasses.action}>
          <div className={treeItemClasses.arrow} aria-label={t("foldUnfold")}>
            {Array.isArray(children) && !!children.length && !expanded && (
              <RafterRight
                title={t("foldUnfold")}
                width={rafterSize}
                height={rafterSize}
              />
            )}

            {showExpandedNodeChildren && (
              <RafterDown
                title={t("foldUnfold")}
                width={rafterSize}
                height={rafterSize}
              />
            )}

            {/* Hide rafter when no children to keep alignment */}
            {!Array.isArray(children) && (
              <RafterRight
                title={t("foldUnfold")}
                width={rafterSize}
                height={rafterSize}
                aria-hidden="true"
              />
            )}
          </div>
          <div
            tabIndex={0}
            role="button"
            className={treeItemClasses.button}
            onClick={handleItemClick}
            onKeyDown={handleItemKeyDown}
          >
            {section && <Folder title={t("folder")} width={20} height={20} />}
            <span className="text-truncate">{label}</span>
          </div>
        </div>

        {showExpandedNodeChildren && <ul role="group">{children}</ul>}
      </div>
    </li>
  );

  return section ? renderSection() : renderItem();
};

export interface TreeViewProps {
  /**
   * TreeNode data
   */
  data: TreeNodeData;

  /**
   * Node ID used for navigation folders
   */
  selectedNodeId?: string;

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
  onTreeItemClick?: (nodeId: string) => void;

  /**
   * Callback function to provide folded item to parent component
   */
  onTreeItemFold?: (nodeId: string) => void;

  /**
   * Callback function to provide unfolded item to parent component
   */
  onTreeItemUnfold?: (nodeId: string) => void;
}

interface TreeNodeProps {
  node: TreeNodeData;
  selectedNodeId: string | undefined;
  expandedNodes: Set<string>;
  elementDragOver?: {
    isOver: boolean;
    overId: string | undefined;
  };
  handleItemClick: (nodeId: string) => void;
}

const TreeNode = ({
  node,
  selectedNodeId,
  expandedNodes,
  elementDragOver,
  handleItemClick,
}: TreeNodeProps) => {
  const selected = selectedNodeId === node.id;
  const expanded = expandedNodes.has(node.id);
  return (
    <TreeItem
      key={node.id}
      nodeId={node.id}
      label={node.name}
      section={node.section}
      selected={selected}
      expanded={expanded}
      focused={elementDragOver?.overId === node.id}
      onItemClick={handleItemClick}
      elementDragOver={elementDragOver}
    >
      {Array.isArray(node.children)
        ? node.children.map((item) => (
            <TreeNode
              key={item.id}
              node={item}
              selectedNodeId={selectedNodeId}
              expandedNodes={expandedNodes}
              handleItemClick={handleItemClick}
              elementDragOver={elementDragOver}
            />
          ))
        : null}
    </TreeItem>
  );
};

/**
 * UI TreeView Component
 */

const TreeView = forwardRef(
  (props: TreeViewProps, ref: Ref<TreeViewHandlers>) => {
    const {
      data,
      onTreeItemClick,
      onTreeItemUnfold,
      elementDragOver,
      selectedNodeId: externalSelectedNodeId,
    } = props;

    const [internalSelectedNodeId, setInternalSelectedNodeId] = useState<
      string | undefined
    >(externalSelectedNodeId ?? undefined);
    const selectedNodeId = externalSelectedNodeId ?? internalSelectedNodeId;
    const expandedNodes = useRef<Set<string>>(new Set());

    useEffect(() => {
      if (elementDragOver?.isOver) {
        elementDragOver.overId
          ? handleItemDrag(elementDragOver.overId)
          : undefined;
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [elementDragOver]);

    /**
     * Doesn't work with lazy loaded data
     * If children are fetched, prefer to use selectedNodeId props
     * And callback props (e.g: onTreeItemClick, onTreeItemUnfold, ...)
     */
    const handlers: TreeViewHandlers = useMemo(
      () => ({
        unselectAll() {
          setInternalSelectedNodeId(undefined);
        },
        select(nodeId: string) {
          handleItemClick(nodeId);
        },
      }),
      // eslint-disable-next-line react-hooks/exhaustive-deps
      [],
    );

    useImperativeHandle(ref, () => handlers, [handlers]);

    /**
     * Effect runs only when controlling treeview with selectedNodeId props
     */
    useEffect(() => {
      if (externalSelectedNodeId) {
        handleExternalSelectedNodeId(externalSelectedNodeId);
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [externalSelectedNodeId]);

    /**
     * If you need to control treeview from a source other than itself
     * @param nodeId
     * @returns
     */
    const handleExternalSelectedNodeId = (nodeId: string) => {
      const isNodeExist = findNodeById(data, externalSelectedNodeId as string);

      if (!isNodeExist) return;

      if (externalSelectedNodeId === "default") {
        expandedNodes.current.forEach((node) => onTreeItemUnfold?.(node));
        return;
      }
      handleExpandNode(nodeId);
    };

    /**
     * Expand a node by adding its ancestors and itself in expandedNodes
     * @param nodeId
     */
    const handleExpandNode = (nodeId: string) => {
      const updatedExpandedNodes = new Set(expandedNodes.current);
      const ancestors = getAncestors(data, nodeId);
      ancestors.forEach((ancestor) => updatedExpandedNodes.add(ancestor));
      updatedExpandedNodes.forEach((node) => onTreeItemUnfold?.(node));
      expandedNodes.current = updatedExpandedNodes;
    };

    /**
     * Collapse a node by deleting it from expandedNodes
     * @param nodeId
     */
    const handleCollapseNode = (nodeId: string) => {
      const updatedExpandedNodes = new Set(expandedNodes.current);
      updatedExpandedNodes.delete(nodeId);
      expandedNodes.current = updatedExpandedNodes;
    };

    /**
     * Expand a node if is not in expandedNodes
     * or
     * Collapse a node if exists in expandedNodes
     * @param nodeId
     */
    const handleToggleNode = (nodeId: string) => {
      expandedNodes.current.has(nodeId)
        ? handleCollapseNode(nodeId)
        : handleExpandNode(nodeId);
    };

    /**
     * Select a node and update internalSelectedNodeId
     * @param nodeId
     * @returns nothing if already selected
     */
    const handleSelectedItem = (nodeId: string) => {
      const isSelected = selectedNodeId === nodeId;

      if (isSelected) return;
      setInternalSelectedNodeId(nodeId);
    };

    /**
     * When using uncontrolled Treeview or TreeviewRef
     * Select a node, expand node and its ancestors
     * If already in expandedNodes, select the node but collapse it in tree
     * @param nodeId
     */
    const handleItemClick = (nodeId: string) => {
      handleSelectedItem(nodeId);
      handleToggleNode(nodeId);
      onTreeItemClick?.(nodeId);
    };

    const handleItemDrag = (nodeId: string) => {
      const isNodeExist = findNodeById(data, externalSelectedNodeId as string);
      if (!isNodeExist) return;
      handleExpandNode(nodeId);
    };

    return (
      <div className="treeview">
        <TreeNode
          node={data}
          selectedNodeId={selectedNodeId}
          expandedNodes={expandedNodes.current}
          handleItemClick={handleItemClick}
          elementDragOver={elementDragOver}
        />
      </div>
    );
  },
);

TreeView.displayName = "TreeView";

export default TreeView;

const getAncestors = (data: TreeNodeData, nodeId: string): string[] => {
  const findItem = findNodeById(data, nodeId);
  if (findItem?.folder?.ancestors) {
    const nodes = findItem?.folder.ancestors || [];
    return [...nodes, nodeId];
  } else {
    return [FOLDER.DEFAULT];
  }
};

const findNodeById = (
  data: TreeNodeData,
  id: string,
): TreeNodeData | undefined => {
  let res: TreeNodeData | undefined;
  if (data?.id === id) {
    return data;
  }
  if (data?.children?.length) {
    data?.children?.every((childNode: TreeNodeData) => {
      res = findNodeById(childNode, id);
      return res === undefined;
    });
  }
  return res;
};

/* const findParentNode = (
  parentNode: TreeNodeData,
  childId: string,
): TreeNodeData | undefined => {
  if (parentNode.children) {
    for (const child of parentNode.children) {
      if (child.id === childId) {
        return parentNode;
      }
      const foundNode = findParentNode(child, childId);
      if (foundNode) {
        return foundNode;
      }
    }
  }
  return undefined;
}; */

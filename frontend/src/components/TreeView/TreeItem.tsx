import { useEffect, useId, useState } from "react";

import { useDroppable } from "@dnd-kit/core";
import { Folder, RafterDown, RafterRight } from "@edifice-ui/icons";
import { useTranslation } from "react-i18next";

import useTreeItemEvents from "./hooks/useTreeItemEvents";

export interface TreeItemProps {
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
   * Node ID used for navigation folders
   */
  selectedNodesIds?: string[];

  /**
   * Callback function to provide selected item to parent component (TreeView)
   */
  onItemSelect?: (nodeId: string) => void;

  /**
   * Callback function to provide folded item to parent component (TreeView)
   */
  onItemFold?: (nodeId: string) => void;

  /**
   * Callback function to provide unfolded item to parent component (TreeView)
   */
  onItemUnfold?: (nodeId: string) => void;

  /**
   * Callback function to provide focused item to parent component (TreeView)
   */
  onItemFocus?: (nodeId: string) => void;

  /**
   * Callback function to provide blured item to parent component (TreeView)
   */
  onItemBlur?: (nodeId: string) => void;

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
    onItemSelect,
    onItemFold,
    onItemUnfold,
    onItemFocus,
    onItemBlur,
    selectedNodesIds,
    elementDragOver,
  } = props;

  const { t } = useTranslation();
  const [expanded, setExpanded] = useState<boolean>(false);

  const {
    handleItemClick,
    handleItemKeyDown,
    handleItemFoldUnfoldClick,
    handleItemFoldUnfoldKeyDown,
    handleItemFocus,
    handleItemBlur,
    handleItemFoldDrag,
  } = useTreeItemEvents(
    nodeId,
    expanded,
    setExpanded,
    onItemSelect,
    onItemFold,
    onItemUnfold,
    onItemFocus,
    onItemBlur,
  );

  const { setNodeRef } = useDroppable({
    id: useId(),
    data: {
      id: nodeId,
      name: label,
      folderTreeview: true,
      accepts: ["folder", "resource"],
    },
  });

  const isFocus = elementDragOver?.overId === nodeId;

  useEffect(() => {
    if (elementDragOver?.overId === nodeId) handleItemFoldDrag();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [elementDragOver]);

  const rafterSize = section ? 16 : 12;

  const renderItem = () => (
    <li
      id={`listitem_${nodeId}`}
      key={nodeId}
      role="treeitem"
      aria-selected={selected}
      aria-expanded={expanded}
    >
      <div>
        <div
          ref={setNodeRef}
          className={`action-container d-flex align-items-center gap-8 px-2 ${
            isFocus ? "drag-focus" : ""
          }`}
        >
          <div
            className={`py-8 ${!Array.isArray(children) ? "invisible" : null}`}
            tabIndex={0}
            role="button"
            onClick={handleItemFoldUnfoldClick}
            onKeyDown={handleItemFoldUnfoldKeyDown}
            aria-label={t("foldUnfold")}
          >
            {Array.isArray(children) && !!children.length && !expanded && (
              <RafterRight
                title={t("foldUnfold")}
                width={rafterSize}
                height={rafterSize}
              />
            )}

            {Array.isArray(children) && !!children.length && expanded && (
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
            className="flex-fill d-flex align-items-center text-truncate gap-8 py-8"
            onClick={handleItemClick}
            onKeyDown={handleItemKeyDown}
            onFocus={handleItemFocus}
            onBlur={handleItemBlur}
          >
            {section && <Folder title={t("folder")} width={20} height={20} />}
            <span className="text-truncate">{label}</span>
          </div>
        </div>

        {Array.isArray(children) && <ul role="group">{children}</ul>}
      </div>
    </li>
  );

  return section ? (
    <ul role="tree" className="m-0 p-0">
      {renderItem()}
    </ul>
  ) : (
    renderItem()
  );
};

TreeItem.displayName = "TreeItem";

export default TreeItem;

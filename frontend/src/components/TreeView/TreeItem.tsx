import { useEffect, useId, useState } from "react";

import { UniqueIdentifier, useDroppable } from "@dnd-kit/core";
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
  onItemSelect?: Function;

  /**
   * Callback function to provide folded item to parent component (TreeView)
   */
  onItemFold?: Function;

  /**
   * Callback function to provide unfolded item to parent component (TreeView)
   */
  onItemUnfold?: Function;

  /**
   * Callback function to provide focused item to parent component (TreeView)
   */
  onItemFocus?: Function;

  /**
   * Callback function to provide blured item to parent component (TreeView)
   */
  onItemBlur?: Function;

  /**
   * Sate element who is drag
   */
  elementDragOver?: {
    isOver: boolean;
    overId: UniqueIdentifier | undefined;
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
    itemFoldDrag,
  } = useTreeItemEvents(
    nodeId,
    label,
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
      accepts: ["folder", "resource"],
    },
  });

  const isFocus = elementDragOver?.overId === nodeId;

  useEffect(() => {
    if (!selectedNodesIds) return;

    const selectedNodesCount = selectedNodesIds.length;

    if (selectedNodesCount >= 1) {
      const lastNodeId = selectedNodesIds[selectedNodesCount - 1] as string;
      console.log({ selectedNodesIds });

      selectedNodesIds.some((node: string) => {
        if (node === nodeId && nodeId !== lastNodeId) {
          setExpanded(true);
          return node === nodeId;
        }
        setExpanded(false);
        return false;
      });
    } else {
      setExpanded(false);
    }
  }, [nodeId, selectedNodesIds]);

  useEffect(() => {
    if (elementDragOver?.overId === nodeId) {
      itemFoldDrag();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [elementDragOver]);

  useEffect(() => {
    if (expanded) console.log({ expanded });
  }, [expanded]);

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

export default function useTreeItemEvents(
  nodeId: string,
  expanded: boolean,
  setExpanded: (value: boolean) => void,
  onItemSelect?: (nodeId: string) => void,
  onItemFold?: (nodeId: string) => void,
  onItemUnfold?: (nodeId: string) => void,
  onItemFocus?: (nodeId: string) => void,
  onItemBlur?: (nodeId: string) => void,
) {
  const handleItemClick = (event: React.MouseEvent<HTMLDivElement>) => {
    event.preventDefault();
    onItemSelect?.(nodeId);
    handleItemFoldUnfold();
    event.stopPropagation();
  };

  const handleItemKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.code === "Enter" || event.code === "Space") {
      event.preventDefault();
      event.stopPropagation();
      onItemSelect?.(nodeId);
      handleItemFoldUnfold();
    }
  };

  const handleItemFoldUnfold = () => {
    setExpanded(!expanded);
    expanded ? onItemFold?.(nodeId) : onItemUnfold?.(nodeId);
  };

  const handleItemFoldDrag = () => {
    setExpanded(true);
    onItemFold?.(nodeId);
  };

  const handleItemFoldUnfoldClick = (
    event: React.MouseEvent<HTMLDivElement>,
  ) => {
    event.preventDefault();
    event.stopPropagation();
    handleItemFoldUnfold();
  };

  const handleItemFoldUnfoldKeyDown = (
    event: React.KeyboardEvent<HTMLDivElement>,
  ) => {
    if (event.code === "Enter" || event.code === "Space") {
      event.preventDefault();
      event.stopPropagation();
      handleItemFoldUnfold();
    }
  };

  const handleItemFocus = (event: React.FocusEvent<HTMLDivElement>) => {
    event.preventDefault();
    onItemFocus?.(nodeId);
    // no need to stop propagation because focus event does not bubble
  };

  const handleItemBlur = (event: React.FocusEvent<HTMLDivElement>) => {
    event.preventDefault();
    onItemBlur?.(nodeId);
    // no need to stop propagation because blur event does not bubble
  };

  return {
    handleItemClick,
    handleItemKeyDown,
    handleItemFoldUnfoldClick,
    handleItemFoldUnfoldKeyDown,
    handleItemFocus,
    handleItemBlur,
    handleItemFoldUnfold,
    handleItemFoldDrag,
  };
}

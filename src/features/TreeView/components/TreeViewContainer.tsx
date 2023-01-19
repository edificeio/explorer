import { useExplorerContext } from "@contexts/index";
import useTreeView from "@features/TreeView/hooks/useTreeView";
import { useI18n } from "@hooks/useI18n";
import { Button, TreeView } from "@ode-react-ui/core";
import { Plus } from "@ode-react-ui/icons";

export const TreeViewContainer = () => {
  const { i18n } = useI18n();
  const { context, state } = useExplorerContext();
  const { treeData } = state;
  /* feature treeview @hook */
  const { handleTreeItemSelect, handleTreeItemFold, handleTreeItemUnfold } =
    useTreeView(context, treeData);

  return (
    <>
      <TreeView
        data={treeData}
        onTreeItemSelect={handleTreeItemSelect}
        onTreeItemFold={handleTreeItemFold}
        onTreeItemUnfold={handleTreeItemUnfold}
      />
      <div className="d-grid my-16">
        <Button
          type="button"
          color="primary"
          variant="outline"
          leftIcon={<Plus />}
        >
          {i18n("explorer.folder.new")}
        </Button>
      </div>
    </>
  );
};

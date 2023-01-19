import { useExplorerContext } from "@contexts/index";
import useTreeView from "@features/TreeView/hooks/useTreeView";
import { Card } from "@ode-react-ui/core";
import { IFolder } from "ode-ts-client";

function FoldersList() {
  const {
    state: { folders },
    selectFolder,
    deselectFolder,
    isFolderSelected,
  } = useExplorerContext();

  const { handleTreeItemSelect } = useTreeView();

  function toggleSelect(folder: IFolder) {
    if (isFolderSelected(folder)) {
      deselectFolder(folder);
    } else {
      selectFolder(folder);
    }
  }
  return folders.length ? (
    <ul className="grid ps-0 list-unstyled">
      {folders.map((folder: IFolder) => {
        return (
          <li className="g-col-4" key={folder.id}>
            <Card
              name={folder.name}
              isFolder
              isSelected={isFolderSelected(folder)}
              onOpen={() => handleTreeItemSelect(folder.id)}
              onSelect={() => toggleSelect(folder)}
            />
          </li>
        );
      })}
    </ul>
  ) : (
    <p>Aucun dossier</p>
  );
}

export default FoldersList;

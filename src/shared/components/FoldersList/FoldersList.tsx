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

  const { handleNavigationFolder } = useTreeView();

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
        const { id, name } = folder;
        return (
          <li className="g-col-4" key={id}>
            <Card
              name={name}
              isFolder
              isSelected={isFolderSelected(folder)}
              onOpen={() =>
                handleNavigationFolder({ folderId: id, folderName: name })
              }
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
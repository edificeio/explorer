import { useExplorerContext } from "@contexts/index";
import { IFolder } from "ode-ts-client";

import { FakeCard } from "../Card";

function FoldersList() {
  const {
    state: { folders },
    selectFolder,
    deselectFolder,
    isFolderSelected,
  } = useExplorerContext();

  function toggleSelect(item: IFolder) {
    if (isFolderSelected(item)) {
      deselectFolder(item);
    } else {
      selectFolder(item);
    }
  }
  return folders.length ? (
    <ul className="grid ps-0">
      {folders.map((folder: IFolder) => {
        return (
          <FakeCard
            key={folder.id}
            {...folder}
            isFolder
            selected={isFolderSelected(folder)}
            onClick={() => toggleSelect(folder)}
          />
        );
      })}
    </ul>
  ) : (
    <p>Aucun dossier</p>
  );
}

export default FoldersList;

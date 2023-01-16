import { useExplorerContext } from "@contexts/index";
import { IFolder } from "ode-ts-client";

import { FakeCard } from "../Card";

function FoldersList() {
  const {
    state: { folders },
  } = useExplorerContext();

  return folders.length ? (
    <ul className="grid ps-0">
      {folders.map((folder: IFolder) => {
        return <FakeCard key={folder.id} {...folder} isFolder />;
      })}
    </ul>
  ) : (
    <p>Aucun dossier</p>
  );
}

export default FoldersList;

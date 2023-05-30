import { IconButton } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { ArrowLeft } from "@ode-react-ui/icons";

import {
  useStoreActions,
  useCurrentFolder,
  useIsTrash,
  useSelectedNodesIds,
} from "~/store";

export function Breadcrumb() {
  const { i18n } = useOdeClient();
  const selectedNodesIds = useSelectedNodesIds();
  const isTrashFolder = useIsTrash();
  const currentFolder = useCurrentFolder();
  const { gotoPreviousFolder } = useStoreActions();

  const trashName: string = i18n("explorer.tree.trash");
  const rootName: string = i18n("explorer.filters.mine");
  const previousName: string = currentFolder?.name || rootName;

  return (
    <div className="py-16">
      {selectedNodesIds.length > 1 && !isTrashFolder ? (
        <div className="d-flex align-items-center gap-8">
          <IconButton
            icon={<ArrowLeft />}
            variant="ghost"
            color="tertiary"
            aria-label={i18n("back")}
            className="ms-n16"
            onClick={gotoPreviousFolder}
          />
          <p className="body py-8 text-truncate">
            <strong>{previousName}</strong>
          </p>
        </div>
      ) : (
        <h2 className="body py-8 fw-bold">
          {!isTrashFolder ? rootName : trashName}
        </h2>
      )}
    </div>
  );
}

import { ArrowLeft } from "@edifice-ui/icons";
import { IconButton, useOdeClient } from "@edifice-ui/react";
import { useTranslation } from "react-i18next";

import {
  useStoreActions,
  useCurrentFolder,
  useIsTrash,
  useSelectedNodesIds,
} from "~/store";

export function ExplorerBreadcrumb() {
  const { appCode } = useOdeClient();
  const { t } = useTranslation(["common", appCode]);
  const selectedNodesIds = useSelectedNodesIds();
  const isTrashFolder = useIsTrash();
  const currentFolder = useCurrentFolder();
  const { gotoPreviousFolder } = useStoreActions();

  const trashName: string = t("explorer.tree.trash");
  const rootName: string = t("explorer.filters.mine", {
    ns: appCode,
  });
  const previousName: string = currentFolder?.name || rootName;

  return (
    <div className="py-16">
      {selectedNodesIds.length > 1 && !isTrashFolder ? (
        <div className="d-flex align-items-center gap-8">
          <IconButton
            icon={<ArrowLeft />}
            variant="ghost"
            color="tertiary"
            aria-label={t("back")}
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
import { useOdeClient, EmptyScreen } from "@ode-react-ui/core";
import { imageBootstrap } from "@shared/constants";
import useExplorerStore from "@store/index";

export function EmptyScreenNoContentInFolder(): JSX.Element | null {
  const { i18n } = useOdeClient();
  const {
    isLoading,
    getHasResourcesOrFolders, // Return number folder or ressources
    getIsTrashSelected, // Return boolean : true if trash is selected, false other
    getHasSelectedRoot, // Return Boolean : true if trash or folder default selected, false other
  } = useExplorerStore((state) => state);

  return getHasResourcesOrFolders() === 0 &&
    !getHasSelectedRoot() &&
    !getIsTrashSelected() &&
    !isLoading ? (
    <EmptyScreen
      imageSrc={`${imageBootstrap}/emptyscreen/illu-noContentInFolder.svg`}
      imageAlt={i18n("explorer.emptyScreen.folder.empty.alt")}
      text={i18n("explorer.emptyScreen.label")}
    />
  ) : null;
}

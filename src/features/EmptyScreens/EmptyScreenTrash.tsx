import { useOdeClient, EmptyScreen } from "@ode-react-ui/core";
import { imageBootstrap } from "@shared/constants";
import useExplorerStore from "@store/index";

export function EmptyScreenTrash(): JSX.Element | null {
  const { i18n } = useOdeClient();
  const {
    getHasResourcesOrFolders, // Return number folder or ressources
    getIsTrashSelected, // Return boolean : true if trash is selected, false other
  } = useExplorerStore((state) => state);

  return getHasResourcesOrFolders() === 0 && getIsTrashSelected() ? (
    <EmptyScreen
      imageSrc={`${imageBootstrap}/emptyscreen/illu-trash.svg`}
      imageAlt={i18n("explorer.emptyScreen.trash.alt")}
      title={i18n("explorer.emptyScreen.trash.title")}
      text={i18n("explorer.emptyScreen.trash.empty")}
    />
  ) : null;
}

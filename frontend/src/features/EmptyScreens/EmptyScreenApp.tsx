import { useOdeClient, EmptyScreen } from "@ode-react-ui/core";
import { imageBootstrap } from "@shared/constants";
import useExplorerStore from "@store/index";

export function EmptyScreenApp(): JSX.Element | null {
  const { i18n, appCode, is1d } = useOdeClient();
  const {
    actions,
    getHasResourcesOrFolders, // Return number folder or ressources
    getIsTrashSelected, // Return boolean : true if trash is selected, false other
    getHasSelectedRoot,
  } = useExplorerStore((state) => state);

  const canCreate = actions.find((action) => action.id === "create");
  const labelEmptyScreenApp = () => {
    if (canCreate?.available && is1d) {
      return i18n("explorer.emptyScreen.blog.txt1d.create");
    } else if (canCreate?.available && !is1d) {
      return i18n("explorer.emptyScreen.blog.txt2d.create");
    } else if (!canCreate?.available && is1d) {
      return i18n("explorer.emptyScreen.blog.txt1d.consultation");
    } else {
      return i18n("explorer.emptyScreen.blog.txt2d.consultation");
    }
  };

  return getHasResourcesOrFolders() === 0 &&
    getHasSelectedRoot() &&
    !getIsTrashSelected() ? (
    <EmptyScreen
      imageSrc={`${imageBootstrap}/emptyscreen/illu-${appCode}.svg`}
      imageAlt={i18n("explorer.emptyScreen.app.alt")}
      title={`${
        canCreate?.available
          ? i18n("explorer.emptyScreen.blog.title.create")
          : i18n("explorer.emptyScreen.blog.title.consultation")
      }`}
      text={labelEmptyScreenApp()}
    />
  ) : null;
}

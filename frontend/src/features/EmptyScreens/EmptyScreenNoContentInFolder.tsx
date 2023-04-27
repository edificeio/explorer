import { useOdeClient, EmptyScreen } from "@ode-react-ui/core";
import { imageBootstrap } from "@shared/constants";

export function EmptyScreenNoContentInFolder(): JSX.Element | null {
  const { i18n } = useOdeClient();

  return (
    <EmptyScreen
      imageSrc={`${imageBootstrap}/emptyscreen/illu-noContentInFolder.svg`}
      imageAlt={i18n("explorer.emptyScreen.folder.empty.alt")}
      text={i18n("explorer.emptyScreen.label")}
    />
  );
}

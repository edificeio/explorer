import { useOdeClient, EmptyScreen } from "@ode-react-ui/core";
import { imageBootstrap } from "@shared/constants";

export function EmptyScreenTrash(): JSX.Element {
  const { i18n } = useOdeClient();

  return (
    <EmptyScreen
      imageSrc={`${imageBootstrap}/emptyscreen/illu-trash.svg`}
      imageAlt={i18n("explorer.emptyScreen.trash.alt")}
      title={i18n("explorer.emptyScreen.trash.title")}
      text={i18n("explorer.emptyScreen.trash.empty")}
    />
  );
}

import { EmptyScreen } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";

export default function EmptyScreenTrash(): JSX.Element {
  const { i18n, theme } = useOdeClient();

  return (
    <EmptyScreen
      imageSrc={`${theme?.bootstrapPath}/images/emptyscreen/illu-trash.svg`}
      imageAlt={i18n("explorer.emptyScreen.trash.alt")}
      title={i18n("explorer.emptyScreen.trash.title")}
      text={i18n("explorer.emptyScreen.trash.empty")}
    />
  );
}

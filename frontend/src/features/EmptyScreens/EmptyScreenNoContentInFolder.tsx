import { EmptyScreen } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";

export default function EmptyScreenNoContentInFolder(): JSX.Element | null {
  const { i18n, theme } = useOdeClient();

  return (
    <EmptyScreen
      imageSrc={`${theme?.bootstrapPath}/images/emptyscreen/illu-noContentInFolder.svg`}
      imageAlt={i18n("explorer.emptyScreen.folder.empty.alt")}
      text={i18n("explorer.emptyScreen.label")}
    />
  );
}

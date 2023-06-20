import { Library as CoreLibrary } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";

const Library = ({ url }: { url: string }) => {
  const { i18n, theme } = useOdeClient();

  return (
    <CoreLibrary
      src={`${theme?.bootstrapPath}/images/image-library.svg`}
      url={url}
      alt={i18n("explorer.libray.img.alt")}
      text={i18n("explorer.libray.title")}
      textButton={i18n("explorer.libray.btn")}
    />
  );
};

export default Library;

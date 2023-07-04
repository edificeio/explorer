import { Library as CoreLibrary } from "@ode-react-ui/components";
import { useTheme } from "@ode-react-ui/core";
import { useTranslation } from "react-i18next";

const Library = ({ url }: { url: string }) => {
  const { t } = useTranslation();
  const { theme } = useTheme();

  return (
    <CoreLibrary
      src={`${theme?.bootstrapPath}/images/image-library.svg`}
      url={url}
      alt={t("explorer.libray.img.alt")}
      text={t("explorer.libray.title")}
      textButton={t("explorer.libray.btn")}
    />
  );
};

export default Library;

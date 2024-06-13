import { ArrowRight } from "@edifice-ui/icons";
import {
  usePaths,
  useOdeTheme,
  useLibraryUrl,
  Image,
  useSession,
} from "@edifice-ui/react";
import { useTranslation } from "react-i18next";

const Library = () => {
  const { t } = useTranslation();
  const sessionQuery = useSession();
  const { theme } = useOdeTheme();
  const libraryUrl = useLibraryUrl();
  const [imagePath] = usePaths();

  // #WB2-1689: add end of year Library gif only for FR users
  const imageFilename =
    sessionQuery?.data?.currentLanguage === "fr"
      ? "image-library-year-end.gif"
      : "image-library.svg";
  const imageFullURL = `${imagePath}/${theme?.bootstrapVersion}/${imageFilename}`;

  return (
    libraryUrl && (
      <div className="p-16">
        <Image
          width="270"
          height="140"
          loading="lazy"
          className="rounded"
          src={imageFullURL}
          alt={t("explorer.libray.img.alt")}
        />
        <p className="m-8">{t("explorer.libray.title")}</p>
        <a
          href={libraryUrl}
          target="_blank"
          rel="noreferrer"
          className="d-inline-flex align-items-center gap-4 btn btn-ghost-primary py-0 p-0 pe-8"
        >
          <span>{t("explorer.libray.btn")}</span>
          <ArrowRight />
        </a>
      </div>
    )
  );
};

export default Library;

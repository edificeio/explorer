import { ArrowRight } from "@edifice-ui/icons";
import { Image } from "@edifice-ui/react";
import { useTranslation } from "react-i18next";
import { useLibrary } from "./useLibrary";

const Library = () => {
  const { libraryUrl, imageFullURL, handleClick } = useLibrary();
  const { t } = useTranslation();

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
        <button
          type="button"
          onClick={handleClick}
          rel="noreferrer"
          className="btn btn-ghost-primary d-inline-flex align-items-center gap-4 p-8"
        >
          <span>{t("explorer.libray.btn")}</span>
          <ArrowRight />
        </button>
      </div>
    )
  );
};

export default Library;

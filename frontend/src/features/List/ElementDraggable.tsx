import { Files } from "@edifice-ui/icons";
import { AppIcon } from "@edifice-ui/react";
import { IWebApp } from "edifice-ts-client";

export const ElementDraggable = ({
  elementType,
  name,
  app,
}: {
  elementType: string;
  name?: string;
  app?: IWebApp;
}) => {
  const iconType = () => {
    if (elementType === "folder") {
      return (
        <Files
          width="24"
          height="24"
          className={`color-app-${app?.displayName}`}
        />
      );
    } else if (elementType === "resource") {
      return <AppIcon app={app} iconFit="ratio" size="24" variant="rounded" />;
    } else {
      return null;
    }
  };
  return (
    <div
      className="d-inline-flex align-items-center card is-selected gap-8"
      style={{ flexDirection: "row", width: "252px", height: "32px" }}
    >
      <div className="ms-8">{iconType()}</div>
      <div className="text-truncate">{name}</div>
    </div>
  );
};

import { UniqueIdentifier, useDraggable } from "@dnd-kit/core";
import { Globe, Users } from "@edifice-ui/icons";
import { OneProfile } from "@edifice-ui/icons/nav";
import {
  CardProps,
  Card,
  AppIcon,
  Avatar,
  Tooltip,
  Image,
} from "@edifice-ui/react";
import { IResource, IWebApp } from "edifice-ts-client";
import { useTranslation } from "react-i18next";

import { useResourceOrFolderIsDraggable } from "~/store";

type OmitChildren = Omit<CardProps, "children">;

export interface ResourceCardProps extends OmitChildren {
  /**
   * IWeb App
   */
  app?: IWebApp | undefined;
  /**
   * Resource
   */
  resource: Partial<IResource>;
  /**
   * Updated date
   */
  time: string;
}

type PickedResource = Pick<IResource, "rights" | "creatorId">;

const ResourceCard = ({
  app,
  resource,
  time,
  isSelected = false,
  isSelectable = true,
  onClick,
  onSelect,
}: ResourceCardProps) => {
  const avatar = `/userbook/avatar/${resource?.creatorId}`;

  function isResourceShared(resource: PickedResource) {
    const { rights, creatorId } = resource || {};
    const filteredRights = rights.filter((right) => !right.includes(creatorId));

    return filteredRights.length >= 1;
  }

  const { attributes, listeners, setNodeRef, transform } = useDraggable({
    id: resource.id as UniqueIdentifier,
    data: {
      resource: resource,
      type: "resource",
    },
  });

  const isShared = isResourceShared(resource as PickedResource);
  const isPublic = resource?.public;
  const resourceOrFolderIsDraggable = useResourceOrFolderIsDraggable();

  const { t } = useTranslation();

  const resourceIsDrag =
    resourceOrFolderIsDraggable.elementDrag === resource.id;

  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      style={
        {
          position: "relative",
          transform: `translate3d(${(transform?.x ?? 0) / 1}px, ${
            (transform?.y ?? 0) / 1
          }px, 0)`,
        } as React.CSSProperties
      }
    >
      <Card
        app={app}
        isSelected={resourceIsDrag && isSelected}
        isSelectable={!resourceIsDrag && isSelectable}
        onClick={onClick}
        onSelect={onSelect}
      >
        {(appCode) => (
          <>
            <Card.Body>
              {!resourceIsDrag ? (
                <div className="card-image medium">
                  {resource?.thumbnail ? (
                    <Image
                      alt=""
                      src={`${resource?.thumbnail}?thumbnail=80x80`}
                      objectFit="cover"
                      className={"h-full w-100"}
                    />
                  ) : (
                    <AppIcon
                      app={app}
                      iconFit="ratio"
                      size="80"
                      variant="rounded"
                    />
                  )}
                </div>
              ) : (
                <AppIcon
                  app={app}
                  iconFit="ratio"
                  size="24"
                  variant="rounded"
                />
              )}
              <div className="text-truncate">
                <Card.Title>{resource?.name}</Card.Title>
                {!resourceIsDrag && (
                  <Card.Text>
                    <em>{time}</em>
                  </Card.Text>
                )}
              </div>
            </Card.Body>
            {!resourceIsDrag && (
              <Card.Footer>
                <div className="d-inline-flex align-items-center gap-8 text-truncate">
                  {avatar ? (
                    <Avatar
                      alt={resource?.creatorName || ""}
                      size="xs"
                      src={avatar}
                      variant="circle"
                      width="24"
                      height="24"
                    />
                  ) : (
                    <OneProfile />
                  )}
                  <Card.Text>{resource?.creatorName}</Card.Text>
                </div>
                <div className="d-inline-flex align-items-center gap-8">
                  {isShared && (
                    <Tooltip
                      message={t("tooltip.shared", { ns: appCode })}
                      placement="top"
                    >
                      <Users width={16} height={16} />
                    </Tooltip>
                  )}
                  {isPublic && (
                    <Tooltip
                      message={t("tooltip.public", { ns: appCode })}
                      placement="top"
                    >
                      <Globe width={16} height={16} />
                    </Tooltip>
                  )}
                </div>
              </Card.Footer>
            )}
          </>
        )}
      </Card>
    </div>
  );
};

ResourceCard.displayName = "ResourceCard";

export default ResourceCard;

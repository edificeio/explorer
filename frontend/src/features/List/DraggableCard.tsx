import { Files } from '@edifice-ui/icons';
import { AppIcon } from '@edifice-ui/react';
import { IWebApp } from 'edifice-ts-client';

function CardIcon({
  app,
  type,
}: {
  app?: IWebApp;
  type: 'folder' | 'resource';
}) {
  if (type === 'folder') {
    return (
      <Files
        width="24"
        height="24"
        className={`color-app-${app?.displayName}`}
      />
    );
  }

  if (type === 'resource') {
    return <AppIcon app={app} iconFit="ratio" size="24" variant="rounded" />;
  }

  return null;
}

export const DraggableCard = ({
  app,
  type,
  name,
}: {
  app?: IWebApp;
  type: 'folder' | 'resource';
  name?: string;
}) => {
  const draggableCardStyles = {
    flexDirection: 'row',
    width: '252px',
    height: '32px',
    boxShadow: '0 0.2rem 0.6em rgba(0, 0, 0, 0.15)',
  } as React.CSSProperties;

  return (
    <div
      className="d-inline-flex align-items-center card is-selected gap-8"
      style={draggableCardStyles}
    >
      <div className="ms-8">
        <CardIcon app={app} type={type} />
      </div>
      <div className="text-truncate">{name}</div>
    </div>
  );
};

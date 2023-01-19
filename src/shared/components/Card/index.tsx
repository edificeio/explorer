import { Avatar } from "@ode-react-ui/core";
import { Files, Users } from "@ode-react-ui/icons";
import { OneProfile } from "@ode-react-ui/icons/nav";
import { useCurrentApp } from "@store/useOdeStore";

export const FakeCard = ({
  name,
  updatedAt,
  creatorName,
  onClick,
  onKeyDown,
  selected,
  isFolder,
}: any) => {
  const currentApp = useCurrentApp();
  const appCode = currentApp?.address.replace("/", "");

  return (
    <li
      className="card g-col-4 shadow border-0"
      role="button"
      tabIndex={0}
      onClick={onClick}
      style={{ backgroundColor: selected ? "#4bafd540" : "transparent" }}
    >
      <div className="card-body p-16 d-flex align-items-center gap-12">
        {!isFolder && <Avatar variant="square" appCode={appCode} />}
        {isFolder && (
          <Files
            width="48"
            height="48"
            className={`color-app-${appCode as string}`}
          />
        )}
        <div>
          <h3 className="card-title body text-truncate text-truncate--2">
            <strong>{name}</strong>
          </h3>
          <span className="card-text small"></span>
        </div>
      </div>
      {!isFolder && (
        <div className="card-footer py-8 px-16 bg-light rounded-2 m-2 border-0 d-flex align-items-center justify-content-between">
          <div className="d-inline-flex align-items-center gap-8">
            <OneProfile />
            <p className="small">{creatorName}</p>
          </div>
          <p className="d-inline-flex align-items-center gap-4 caption">
            <Users width={16} height={16} /> <strong>23</strong>
          </p>
        </div>
      )}
    </li>
  );
};

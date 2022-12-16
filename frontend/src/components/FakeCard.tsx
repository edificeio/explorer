import { AppCard } from "@ode-react-ui/core";
import { Users } from "@ode-react-ui/icons";
import { OneProfile } from "@ode-react-ui/icons/nav";

function FakeCard({
  currentApp,
  name,
  updatedAt,
  creatorName,
  onClick,
  onKeyDown,
}: any) {
  return (
    <div
      className="card g-col-4 shadow border-0"
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={onKeyDown}
    >
      <div className="card-body p-16 d-flex align-items-center gap-12">
        <AppCard app={currentApp} variant="square">
          <AppCard.Icon size="48" />
        </AppCard>
        <div>
          <h3 className="card-title body">
            <strong>{name}</strong>
          </h3>
          <span className="card-text small">
            <em>{updatedAt}</em>
          </span>
        </div>
      </div>
      <div className="card-footer py-8 px-16 bg-light rounded-2 m-2 border-0 d-flex align-items-center justify-content-between">
        <div className="d-inline-flex align-items-center gap-8">
          <OneProfile />
          <p className="small">{creatorName}</p>
        </div>
        <p className="d-inline-flex align-items-center gap-4 caption">
          <Users width={16} height={16} /> <strong>23</strong>
        </p>
      </div>
    </div>
  );
}

export default FakeCard;

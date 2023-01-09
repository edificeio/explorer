import dayjs from "@config/dayjs";
import { useOdeContext } from "@contexts/OdeContext/OdeContext";
import { Avatar } from "@ode-react-ui/core";
import { Users } from "@ode-react-ui/icons";
import { OneProfile } from "@ode-react-ui/icons/nav";

export const FakeCard = ({
  name,
  updatedAt,
  creatorName,
  onClick,
  onKeyDown,
  selected,
}: any) => {
  const { appCode } = useOdeContext();
  return (
    <div
      className="card g-col-4 shadow border-0"
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={onKeyDown}
      style={{ backgroundColor: selected ? "#4bafd540" : "transparent" }}
    >
      <div className="card-body p-16 d-flex align-items-center gap-12">
        <Avatar variant="square" appCode={appCode} />
        <div>
          <h3 className="card-title body text-truncate text-truncate--2">
            <strong>{name}</strong>
          </h3>
          <span className="card-text small">
            {/* <em>{dayjs(updatedAt).format("DD/MM/YYYY")}</em> */}
            <em>{dayjs(updatedAt).fromNow()}</em>
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
};

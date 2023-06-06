/* eslint-disable jsx-a11y/click-events-have-key-events */
import { useI18n } from "@ode-react-ui/core";
import { Delete } from "@ode-react-ui/icons";

export interface TrashButtonProps {
  id: string;
  selected: boolean;
  onSelect: () => void;
}

export const TrashButton = ({
  id,
  selected,
  onSelect,
}: TrashButtonProps): JSX.Element | null => {
  const { i18n } = useI18n();

  return (
    <div className="treeview">
      <ul role="tree" className="m-0 p-0">
        <li id={id} role="treeitem" aria-selected={selected}>
          <div>
            <div className="action-container">
              <div onClick={onSelect} role="button" tabIndex={0}>
                <div className="d-flex align-items-center gap-8 py-8 ps-24">
                  <Delete width="20" height="20" />
                  <span>{i18n("explorer.tree.trash")}</span>
                </div>
              </div>
            </div>
          </div>
        </li>
      </ul>
    </div>
  );
};

export default TrashButton;

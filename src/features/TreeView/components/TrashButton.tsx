import { useExplorerContext } from "@contexts/index";
import { Delete } from "@ode-react-ui/icons";
export interface TrashButtonProps {
  id: string;
  selected: boolean;
  onSelect: () => void;
}
export const TrashButton = ({ id, selected, onSelect }: TrashButtonProps) => {
  const { i18n } = useExplorerContext();
  return (
    <>
      <div className="treeview">
        <ul role="tree" className="m-0 p-0">
          <li id={id} role="treeitem" aria-selected={selected}>
            <div>
              <div className="action-container d-flex align-items-center gap-8 px-2">
                <div
                  onClick={(e) => onSelect()}
                  onKeyPress={(e) => onSelect()}
                  role="button"
                  tabIndex={0}
                >
                  <div className="flex-fill d-flex align-items-center gap-16 py-8">
                    <Delete title="Delete" />
                    <span>{i18n("explorer.tree.trash")}</span>
                  </div>
                </div>
              </div>
            </div>
          </li>
        </ul>
      </div>
    </>
  );
};

export default TrashButton;

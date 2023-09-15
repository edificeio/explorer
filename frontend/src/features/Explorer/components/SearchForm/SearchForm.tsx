import { Filter } from "@edifice-ui/icons";
import {
  FormControl,
  Input,
  SearchButton,
  Dropdown,
  DropdownTrigger,
  SelectList,
  useOdeClient,
  type OptionListItemType,
} from "@edifice-ui/react";
import { useTranslation } from "react-i18next";

interface SearchFormProps {
  options: OptionListItemType[];
}

export const SearchForm = ({ options }: SearchFormProps) => {
  const { t } = useTranslation();
  const { appCode } = useOdeClient();

  return (
    <form
      noValidate
      className="bg-light p-16 ps-24 ms-n16 ms-lg-n24 me-n16 position-relative z-3 d-flex gap-8"
    >
      <FormControl id="search" className="input-group">
        <Input
          type="search"
          placeholder={t("explorer.label.search", { ns: appCode })}
          size="lg"
          noValidationIcon
        />
        <SearchButton
          type="submit"
          aria-label={t("explorer.label.search", { ns: appCode })}
        />
      </FormControl>
      <Dropdown
        content={
          <SelectList
            model={[]}
            onChange={() => {
              console.log("works");
            }}
            options={options}
          />
        }
        trigger={
          <DropdownTrigger
            icon={<Filter width={20} />}
            title={t("Filtres")}
            variant="ghost"
          />
        }
      />
    </form>
  );
};

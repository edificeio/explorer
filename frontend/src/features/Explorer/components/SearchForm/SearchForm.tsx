import { Filter } from "@edifice-ui/icons";
import {
  FormControl,
  Input,
  SearchButton,
  Dropdown,
  DropdownTrigger,
  SelectList,
  useOdeClient,
} from "@edifice-ui/react";
import { useTranslation } from "react-i18next";

import { useSearchForm } from "../../hooks/useSearchForm";
import { useSelectedFilters } from "../../hooks/useSelectedFilters";

export const SearchForm = () => {
  const { appCode } = useOdeClient();
  const { t } = useTranslation();

  const { selectedFilters, options, setSelectedFilters } = useSelectedFilters();

  const {
    inputSearch,
    handleInputSearchChange,
    handleKeyPress,
    handleSearchSubmit,
  } = useSearchForm();

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
          value={inputSearch}
          onChange={handleInputSearchChange}
          onKeyDown={handleKeyPress}
        />
        <SearchButton
          type="submit"
          aria-label={t("explorer.label.search", { ns: appCode })}
          onClick={handleSearchSubmit}
        />
      </FormControl>
      <Dropdown
        content={
          <SelectList
            isMonoSelection
            model={selectedFilters}
            onChange={(filter) => setSelectedFilters(filter)}
            options={options}
          />
        }
        trigger={
          <DropdownTrigger
            icon={<Filter width={20} />}
            title={t("explorer.filters")}
            variant="ghost"
            badgeContent={
              selectedFilters.length > 0 ? selectedFilters.length : undefined
            }
          />
        }
      />
    </form>
  );
};

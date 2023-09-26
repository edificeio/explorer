import {
  ChangeEvent,
  KeyboardEvent,
  MouseEvent,
  useDeferredValue,
  useEffect,
  useState,
} from "react";

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
  useDebounce,
} from "@edifice-ui/react";
import { useTranslation } from "react-i18next";

import { useCurrentFolder, useStoreActions } from "~/store";

interface SearchFormProps {
  options: OptionListItemType[];
}

export const SearchForm = ({ options }: SearchFormProps) => {
  const [selectedFilters, setSelectedFilters] = useState<(string | number)[]>(
    [],
  );
  const [inputSearch, setInputSearch] = useState<string>("");
  const deferredInputSearch = useDeferredValue(inputSearch);
  const debounceInputSearch = useDebounce<string>(inputSearch, 500);
  const { t } = useTranslation();
  const { appCode } = useOdeClient();
  const currentFolder = useCurrentFolder();
  const { setSearchParams } = useStoreActions();

  const handleInputSearchChange = (event: ChangeEvent<HTMLInputElement>) => {
    const newText = event.target.value;
    setInputSearch(newText);
  };
  const handleKeyPress = (event: KeyboardEvent): void => {
    if (event.key === "Enter" || event.key === "Return") {
      setSearchParams({
        search: debounceInputSearch ? debounceInputSearch : undefined,
      });
      event.preventDefault();
    }
  };
  const handleSearchSubmit = (e: MouseEvent): void => {
    setSearchParams({
      search: debounceInputSearch ? debounceInputSearch : undefined,
    });
    e.preventDefault();
  };
  useEffect(() => {
    const isOwnerSelected = (): boolean | undefined => {
      return selectedFilters.includes(1) ? true : undefined;
    };

    const isSharedSelected = (): boolean | undefined => {
      return selectedFilters.includes(2) ? true : undefined;
    };

    const isPublicSelected = (): boolean | undefined => {
      return selectedFilters.includes(7) ? true : undefined;
    };
    // auto update search only if searchbar is empty or have at least 3 caracters => else need manual action (enter or click button)
    const shouldUpdateSearch =
      debounceInputSearch.length == 0 || debounceInputSearch.length >= 3;
    const searchPartial = shouldUpdateSearch
      ? { search: debounceInputSearch ? debounceInputSearch : undefined }
      : {};
    setSearchParams({
      ...searchPartial,
      filters: {
        owner: isOwnerSelected(),
        public: isPublicSelected(),
        shared: isSharedSelected(),
        folder: currentFolder ? currentFolder.id : "default",
      },
    });
  }, [debounceInputSearch, selectedFilters, currentFolder, setSearchParams]);

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
          value={deferredInputSearch}
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
            model={selectedFilters}
            onChange={(filter) => {
              setSelectedFilters(filter);
            }}
            options={options}
          />
        }
        trigger={
          <DropdownTrigger
            icon={<Filter width={20} />}
            title={t("Filtres ")}
            variant="ghost"
          />
        }
      />
    </form>
  );
};

import { useEffect, useState } from "react";

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

import { useCurrentFolder, useSearchParams, useStoreActions } from "~/store";

interface SearchFormProps {
  options: OptionListItemType[];
}

export const SearchForm = ({ options }: SearchFormProps) => {
  const [selectedFilters, setSelectedFilters] = useState<(string | number)[]>(
    [],
  );
  const { t } = useTranslation();
  const { appCode } = useOdeClient();
  const currentFolder = useCurrentFolder();
  const searchParams = useSearchParams();
  const { setSearchParams } = useStoreActions();

  const isOwnerSelected = (): boolean | undefined => {
    return selectedFilters.includes(1) ? true : undefined;
  };

  const isSharedSelected = (): boolean | undefined => {
    return selectedFilters.includes(2) ? true : undefined;
  };

  const isPublicSelected = (): boolean | undefined => {
    return selectedFilters.includes(7) ? true : undefined;
  };

  useEffect(() => {
    setSearchParams({
      ...searchParams,
      filters: {
        owner: isOwnerSelected(),
        public: isPublicSelected(),
        shared: isSharedSelected(),
        folder: currentFolder ? currentFolder.id : "default",
      },
    });
  }, [selectedFilters]);

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
            isMonoSelection
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

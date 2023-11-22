/* eslint-disable react-hooks/exhaustive-deps */
import { useState, useEffect } from "react";

import { useDebounce } from "@edifice-ui/react";

import {
  useSearchConfig,
  useSearchParams,
  useStoreActions,
  useTreeStatus,
} from "~/store";

export const useSearchForm = () => {
  const searchParams = useSearchParams();
  const [inputSearch, setInputSearch] = useState<string>("");
  const debounceInputSearch = useDebounce<string>(inputSearch, 500);
  const searchConfig = useSearchConfig();
  const status = useTreeStatus();

  const { setSearchParams } = useStoreActions();

  const handleInputSearchChange = (
    event: React.ChangeEvent<HTMLInputElement>,
  ) => {
    const newText = event.target.value;
    setInputSearch(newText.toString());
  };

  const handleKeyPress = (event: React.KeyboardEvent): void => {
    if (event.key === "Enter" || event.key === "Return") {
      event.preventDefault();
      setSearchParams({
        search: inputSearch ? inputSearch : undefined,
      });
    }
  };

  const handleSearchSubmit = (e: React.MouseEvent): void => {
    e.preventDefault();
    setSearchParams({
      search: debounceInputSearch ? debounceInputSearch : undefined,
    });
  };

  useEffect(() => {
    // auto update search only if searchbar is empty or have at least X caracters => else need manual action (enter or click button)
    const shouldUpdateSearch =
      debounceInputSearch.length == 0 ||
      debounceInputSearch.length >= searchConfig.minLength;

    const searchPartial = shouldUpdateSearch
      ? {
          search: debounceInputSearch ? debounceInputSearch : undefined,
        }
      : {};

    setSearchParams({
      ...searchParams,
      ...searchPartial,
    });
  }, [debounceInputSearch, searchConfig.minLength]);

  useEffect(() => {
    if (status === "select") setInputSearch("");
  }, [status]);

  useEffect(() => {
    setInputSearch(() => searchParams.search?.toString() ?? "");
  }, [searchParams]);

  return {
    inputSearch,
    handleInputSearchChange,
    handleKeyPress,
    handleSearchSubmit,
  };
};

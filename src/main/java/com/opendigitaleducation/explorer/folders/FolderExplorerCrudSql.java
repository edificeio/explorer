package com.opendigitaleducation.explorer.folders;

import com.opendigitaleducation.explorer.plugin.ExplorerResourceCrudSql;
import com.opendigitaleducation.explorer.postgres.PostgresClient;

import java.util.Arrays;
import java.util.List;

public class FolderExplorerCrudSql extends ExplorerResourceCrudSql {

    public FolderExplorerCrudSql(final PostgresClient pool) {
        super(pool.getClientPool());
    }

    @Override
    protected String getTableName() { return "explorer.folders"; }

    @Override
    protected List<String> getColumns() { return Arrays.asList("name", "application", "resource_type", "parent_id", "creator_id", "creator_name"); }

}

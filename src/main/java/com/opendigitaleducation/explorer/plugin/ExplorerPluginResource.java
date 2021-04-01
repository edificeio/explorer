package com.opendigitaleducation.explorer.plugin;

public abstract class ExplorerPluginResource extends ExplorerPlugin {
    protected ExplorerPluginResource(ExplorerPluginCommunication communication) {
        super(communication);
    }

    @Override
    protected boolean isForSearch() {
        return false;
    }
}
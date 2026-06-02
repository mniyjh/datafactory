package com.cqie.datafactory.executor.service;

public interface NodeSyncService {
    void markTaskNodesOutdated(Long componentId, String componentVersion);

    void syncNodeSnapshot(Long taskDslId);
}

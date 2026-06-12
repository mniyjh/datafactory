package com.cqie.datafactory.executor.controller;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.service.ComponentSnapshotService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/component-snapshot")
public class ComponentSnapshotController {

    private final ComponentSnapshotService componentSnapshotService;

    public ComponentSnapshotController(ComponentSnapshotService componentSnapshotService) {
        this.componentSnapshotService = componentSnapshotService;
    }

    @PostMapping("/{taskDslId}/rebuild")
    @PreAuthorize("hasAuthority('task:write')")
    public Result<Void> rebuild(@PathVariable("taskDslId") Long taskDslId) {
        componentSnapshotService.rebuildNodeSnapshotByTaskDsl(taskDslId);
        return Result.success();
    }
}

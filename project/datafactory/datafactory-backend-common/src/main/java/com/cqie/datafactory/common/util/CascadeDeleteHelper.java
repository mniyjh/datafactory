package com.cqie.datafactory.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用“先删子后删主”删除编排器。
 */
public class CascadeDeleteHelper {

    private final List<Runnable> childDeleteActions = new ArrayList<>();
    private Runnable parentDeleteAction;

    public CascadeDeleteHelper addChildDelete(Runnable action) {
        if (action != null) {
            childDeleteActions.add(action);
        }
        return this;
    }

    public CascadeDeleteHelper setParentDelete(Runnable action) {
        this.parentDeleteAction = action;
        return this;
    }

    public void execute() {
        for (Runnable action : childDeleteActions) {
            action.run();
        }
        if (parentDeleteAction != null) {
            parentDeleteAction.run();
        }
    }
}

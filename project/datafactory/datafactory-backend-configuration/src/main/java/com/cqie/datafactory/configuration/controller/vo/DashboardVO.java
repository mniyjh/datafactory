package com.cqie.datafactory.configuration.controller.vo;

import lombok.Data;

@Data
public class DashboardVO {
    private long dbCount;
    private long apiCount;
    private long scriptCount;
    private long taskCount;
}

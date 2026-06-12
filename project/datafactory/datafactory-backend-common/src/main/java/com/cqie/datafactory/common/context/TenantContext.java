package com.cqie.datafactory.common.context;

public final class TenantContext {
    private static final ThreadLocal<Long> TENANT_HOLDER = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long tenantId) {
        TENANT_HOLDER.set(tenantId);
    }

    public static Long get() {
        return TENANT_HOLDER.get();
    }

    public static void clear() {
        TENANT_HOLDER.remove();
    }
}

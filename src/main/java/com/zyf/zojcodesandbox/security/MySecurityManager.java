package com.zyf.zojcodesandbox.security;

import java.io.FileDescriptor;
import java.security.Permission;

public class MySecurityManager extends SecurityManager {
    @Override
    public void checkPermission(Permission perm) {

    }
    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("无权限：" + cmd);
    }

    @Override
    public void checkWrite(String file) {
        throw new SecurityException("无权限：" + file);
    }

    @Override
    public void checkDelete(String file) {
        throw new SecurityException("无权限：" + file);
    }
}

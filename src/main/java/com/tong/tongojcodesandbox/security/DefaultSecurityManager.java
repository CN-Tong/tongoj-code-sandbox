package com.tong.tongojcodesandbox.security;

/**
 * 默认安全管理器
 */
public class DefaultSecurityManager extends SecurityManager {

    /**
     * 限制执行文件权限
     * @param cmd
     */
    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("checkExec 权限异常：" + cmd);
    }

    /**
     * 限制网络连接权限
     * @param host
     * @param port
     */
    @Override
    public void checkConnect(String host, int port) {
        throw new SecurityException("checkConnect 权限异常：" + host + ":" + port);
    }

    /**
     * 限制写文件权限
     * @param file
     */
    @Override
    public void checkWrite(String file) {
        throw new SecurityException("checkWrite 权限异常：" + file);
    }

    /**
     * 限制读文件权限
     * @param file
     */
    // @Override
    // public void checkRead(String file) {
    //     throw new SecurityException("checkRead 权限异常：" + file);
    // }
}

package com.zyf.zojcodesandbox;


import com.zyf.zojcodesandbox.model.ExecuteCodeRequest;
import com.zyf.zojcodesandbox.model.ExecuteCodeResponse;

/**
 * 代码沙箱接口
 */
public interface CodeSandbox {
    /**
     * 在代码沙箱中执行代码
     *
     * @param executeCodeRequest 执行代码请求信息
     * @return 执行代码响应信息
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}

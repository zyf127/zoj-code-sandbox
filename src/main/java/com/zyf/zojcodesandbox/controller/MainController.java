package com.zyf.zojcodesandbox.controller;

import com.zyf.zojcodesandbox.JavaCodeSandboxTemplate;
import com.zyf.zojcodesandbox.model.ExecuteCodeRequest;
import com.zyf.zojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController("/")
public class MainController {

    @Resource
    private JavaCodeSandboxTemplate javaDockerCodeSandbox;

    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET_KEY = "hvIbPRZYipMSREJ/ZlCMTx9/+sOe3igjz9d0XqhcPOA=";

    @GetMapping("/health")
    public String checkHealth() {
        return "ok";
    }

    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request, HttpServletResponse response) {
        String secretKey = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET_KEY.equals(secretKey)) {
            response.setStatus(403);
            return new ExecuteCodeResponse();
        }
        return javaDockerCodeSandbox.executeCode(executeCodeRequest);
    }
}

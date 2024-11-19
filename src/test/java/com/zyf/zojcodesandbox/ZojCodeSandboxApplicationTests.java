package com.zyf.zojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import com.zyf.zojcodesandbox.model.ExecuteCodeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@SpringBootTest
class ZojCodeSandboxApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void testJavaNativeCodeSandbox() {
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setInputList(Arrays.asList("1 2"));
        executeCodeRequest.setLanguage("Java");

        CodeSandboxTemplate codeSandboxTemplate = new NativeCodeSandbox();
        System.out.println(codeSandboxTemplate.executeCode(executeCodeRequest));
    }

    @Test
    void testJavaDockerCodeSandbox() {
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        executeCodeRequest.setLanguage("Java");

        CodeSandboxTemplate codeSandboxTemplate = new DockerCodeSandbox();
        System.out.println(codeSandboxTemplate.executeCode(executeCodeRequest));
    }

}

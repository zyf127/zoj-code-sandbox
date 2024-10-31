package com.zyf.zojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import com.zyf.zojcodesandbox.model.ExecuteCodeRequest;
import com.zyf.zojcodesandbox.model.ExecuteCodeResponse;
import com.zyf.zojcodesandbox.model.ExecuteMessage;
import com.zyf.zojcodesandbox.utils.ProcessUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaNativeCodeSandbox implements CodeSandbox {

    private static final String USER_DIR = "user.dir";

    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    private static final String GLOBAL_JAVA_NAME = "Main.java";


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String language = executeCodeRequest.getLanguage();
        String code = executeCodeRequest.getCode();
        // 1. 把用户的代码存放在相应的目录中（tempCode/UUID/Main.java）
        String userDir = System.getProperty(USER_DIR);
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        // 2. 编译Java代码，得到class文件
        String compileCmd = String.format("javac -encoding UTF-8 %s", userCodeFile.getAbsolutePath());
        Process complieProcess = null;
        try {
            complieProcess = Runtime.getRuntime().exec(compileCmd);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ExecuteMessage compileMessage = ProcessUtils.runProcess(complieProcess);
        System.out.println(compileMessage);
        // 3. 运行代码，得到输出结果
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            Process runProcess = null;
            try {
                runProcess = Runtime.getRuntime().exec(runCmd);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            ExecuteMessage runMessage = ProcessUtils.runProcess(runProcess);
            System.out.println(runMessage);
        }
        return null;
    }

    public static void main(String[] args) {
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setInputList(Arrays.asList("1 2"));
        executeCodeRequest.setLanguage("Java");

        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        System.out.println(javaNativeCodeSandbox.executeCode(executeCodeRequest));
    }
}

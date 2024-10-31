package com.zyf.zojcodesandbox.utils;

import com.zyf.zojcodesandbox.model.ExecuteMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 进程工具类
 */
public class ProcessUtils {
    /**
     * 执行进程
     *
     * @param process
     * @return
     */
    public static ExecuteMessage runProcess(Process process) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            // 等待程序执行，获取响应码
            int exitValue = process.waitFor();
            executeMessage.setExitValue(exitValue);
            if (exitValue == 0) {
                // 正常退出
                // 分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                // 逐行读取
                String line;
                StringBuilder stringBuilder = new StringBuilder();
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                executeMessage.setMessage(stringBuilder.toString());
            } else {
                // 异常退出
                // 分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                // 逐行读取
                String line;
                StringBuilder stringBuilder = new StringBuilder();
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                executeMessage.setMessage(stringBuilder.toString());

                // 分批获取进程的错误输出
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                // 逐行读取
                String errorLine;
                StringBuilder errorStringBuilder = new StringBuilder();
                while ((errorLine = errorBufferedReader.readLine()) != null) {
                    errorStringBuilder.append(errorLine);
                }
                executeMessage.setErrorMessage(errorStringBuilder.toString());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            process.destroy();
        }
        return executeMessage;
    }
}

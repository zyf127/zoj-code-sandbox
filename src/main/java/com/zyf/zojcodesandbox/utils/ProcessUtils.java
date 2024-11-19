package com.zyf.zojcodesandbox.utils;

import com.zyf.zojcodesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.*;

/**
 * 进程工具类
 */
public class ProcessUtils {
    /**
     * 执行编译文件进程
     *
     * @param compileProcess
     * @return
     */
    public static ExecuteMessage compileFileProcess(Process compileProcess) throws InterruptedException, IOException {
        ExecuteMessage executeMessage = new ExecuteMessage();
        // StopWatch用于获取程序运行时间
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        // 等待程序执行，获取响应码
        int exitValue = compileProcess.waitFor();
        executeMessage.setExitValue(exitValue);
        if (exitValue == 0) {
            // 正常退出
            // 分批获取进程的正常输出
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()));
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
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()));
            // 逐行读取
            String line;
            StringBuilder stringBuilder = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            executeMessage.setMessage(stringBuilder.toString());

            // 分批获取进程的错误输出
            BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(compileProcess.getErrorStream()));
            // 逐行读取
            String errorLine;
            StringBuilder errorStringBuilder = new StringBuilder();
            while ((errorLine = errorBufferedReader.readLine()) != null) {
                errorStringBuilder.append(errorLine);
            }
            executeMessage.setErrorMessage(errorStringBuilder.toString());
        }
        stopWatch.stop();
        long lastTaskTimeMillis = stopWatch.getLastTaskTimeMillis();
        executeMessage.setTime(lastTaskTimeMillis);
        compileProcess.destroy();
        return executeMessage;
    }

    /**
     * 执行交互式进程
     *
     * @param runProcess
     * @param inputArgs
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    public static ExecuteMessage runFileProcess(Process runProcess, String inputArgs) throws InterruptedException, IOException {
        StringReader inputReader = new StringReader(inputArgs);
        BufferedReader inputBufferedReader = new BufferedReader(inputReader);

        // StopWatch用于获取程序运行时间
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // 输入（模拟控制台输入）
        PrintWriter consoleInput = new PrintWriter(runProcess.getOutputStream());
        String line;
        while ((line = inputBufferedReader.readLine()) != null) {
            consoleInput.println(line);
            consoleInput.flush();
        }
        consoleInput.close();

        // 获取输出
        BufferedReader userCodeOutput = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
        StringBuilder outputStringBuilder = new StringBuilder();
        String outputLine;
        while ((outputLine = userCodeOutput.readLine()) != null) {
            outputStringBuilder.append(outputLine);
        }
        userCodeOutput.close();

        // 获取错误输出
        BufferedReader errorOutput = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
        StringBuilder errorStringBuilder = new StringBuilder();
        String errorLine;
        while ((errorLine = errorOutput.readLine()) != null) {
            errorStringBuilder.append(errorLine);
        }
        errorOutput.close();

        stopWatch.stop();
        ExecuteMessage executeMessage = new ExecuteMessage();
        executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        executeMessage.setMessage(outputStringBuilder.toString());
        executeMessage.setErrorMessage(errorStringBuilder.toString());
        runProcess.destroy();
        return executeMessage;
    }
}

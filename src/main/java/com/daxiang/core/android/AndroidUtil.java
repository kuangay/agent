package com.daxiang.core.android;

import com.android.ddmlib.*;
import com.daxiang.utils.Terminal;
import com.daxiang.utils.UUIDUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jiangyitao.
 */
@Slf4j
public class AndroidUtil {


    public static final Map<String, String> ANDROID_VERSION = new HashMap();

    static {
        // https://source.android.com/setup/start/build-numbers
        // uiautomator需要 >= 4.2
        ANDROID_VERSION.put("17", "4.2");
        ANDROID_VERSION.put("18", "4.3");
        ANDROID_VERSION.put("19", "4.4");
        ANDROID_VERSION.put("20", "4.4W");
        ANDROID_VERSION.put("21", "5.0");
        ANDROID_VERSION.put("22", "5.1");
        ANDROID_VERSION.put("23", "6.0");
        ANDROID_VERSION.put("24", "7.0");
        ANDROID_VERSION.put("25", "7.1");
        ANDROID_VERSION.put("26", "8.0");
        ANDROID_VERSION.put("27", "8.1");
        ANDROID_VERSION.put("28", "9");
        ANDROID_VERSION.put("29", "10");
    }

    public static String getCpuInfo(IDevice iDevice) throws IDeviceExecuteShellCommandException {
        String cpuInfo = executeShellCommand(iDevice, "cat /proc/cpuinfo |grep Hardware"); // Hardware	: Qualcomm Technologies, Inc MSM8909
        if (StringUtils.isEmpty(cpuInfo) || !cpuInfo.startsWith("Hardware")) {
            throw new RuntimeException("获取CPU信息失败");
        }
        return cpuInfo.split(":")[1].trim();
    }

    public static String getMemSize(IDevice iDevice) throws IDeviceExecuteShellCommandException {
        String memInfo = executeShellCommand(iDevice, "cat /proc/meminfo |grep MemTotal"); // MemTotal:        1959700 kB
        if (StringUtils.isEmpty(memInfo) || !memInfo.startsWith("MemTotal")) {
            throw new RuntimeException("获取内存信息失败");
        }

        String memKB = Pattern.compile("[^0-9]").matcher(memInfo).replaceAll("").trim();
        return Math.ceil(Long.parseLong(memKB) / (1024.0 * 1024)) + " GB";
    }

    public static String getDeviceName(IDevice iDevice) {
        return "[" + iDevice.getProperty("ro.product.brand") + "] " + iDevice.getProperty("ro.product.model");
    }

    public static String getAndroidVersion(IDevice iDevice) {
        return ANDROID_VERSION.get(getSdkVersion(iDevice));
    }

    /**
     * 通过minicap截图
     *
     * @param iDevice
     * @param resolution 手机分辨率 eg. 1080x1920
     */
    public static File screenshotByMinicap(IDevice iDevice, String resolution, int orientation) throws Exception {
        String localFilePath = UUIDUtil.getUUID() + ".jpg";
        String remoteFilePath = AndroidDevice.TMP_FOLDER + "minicap.jpg";

        String screenshotCmd = String.format("LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/minicap -P %s@%s/%d -s >%s", resolution, resolution, orientation, remoteFilePath);
        String minicapOutput = executeShellCommand(iDevice, screenshotCmd);

        if (StringUtils.isEmpty(minicapOutput) || !minicapOutput.contains("bytes for JPG encoder")) {
            throw new RuntimeException("minicap截图失败, cmd: " + screenshotCmd + ", minicapOutput: " + minicapOutput);
        }

        // pull到本地
        iDevice.pullFile(remoteFilePath, localFilePath);
        return new File(localFilePath);
    }

    /**
     * 获取CPU架构
     *
     * @return
     */
    public static String getCpuAbi(IDevice iDevice) {
        return iDevice.getProperty("ro.product.cpu.abi");
    }

    public static String getSdkVersion(IDevice iDevice) {
        return iDevice.getProperty("ro.build.version.sdk");
    }

    /**
     * 等待设备上线
     */
    public static void waitForDeviceOnline(IDevice iDevice, long maxWaitTimeInSeconds) {
        long startTime = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() - startTime > maxWaitTimeInSeconds * 1000) {
                throw new RuntimeException("[" + iDevice.getSerialNumber() + "]设备未上线");
            }
            if (iDevice.isOnline()) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    public static void installApk(IDevice iDevice, String apkPath) throws InstallException {
        iDevice.installPackage(apkPath, true);
    }

    public static void uninstallApk(IDevice iDevice, String packageName) throws InstallException {
        iDevice.uninstallPackage(packageName);
    }

    /**
     * 执行命令
     *
     * @param cmd
     * @return
     */
    public static String executeShellCommand(IDevice iDevice, String cmd) throws IDeviceExecuteShellCommandException {
        Assert.notNull(iDevice, "iDevice can not be null");
        Assert.hasText(cmd, "cmd can not be empty");

        log.info("[{}==>]", cmd);
        CollectingOutputReceiver collectingOutputReceiver = new CollectingOutputReceiver();
        try {
            iDevice.executeShellCommand(cmd, collectingOutputReceiver);
        } catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException e) {
            throw new IDeviceExecuteShellCommandException(e);
        }
        String response = collectingOutputReceiver.getOutput();
        log.info("[{}<==]{}", cmd, response);
        return response;
    }

    /**
     * aapt dump badging
     */
    public static String aaptDumpBadging(String apkPath) throws IOException {
        return Terminal.execute("aapt dump badging " + apkPath);
    }

    public static void clearApkData(IDevice iDevice, String packageName) throws IDeviceExecuteShellCommandException {
        executeShellCommand(iDevice, "pm clear " + packageName);
    }

    public static void restartApk(IDevice iDevice, String packageName, String launchActivity) throws IDeviceExecuteShellCommandException {
        executeShellCommand(iDevice, "am start -S -n " + packageName + "/" + launchActivity);
    }

    /**
     * 获取屏幕分辨率
     *
     * @return eg.720x1280
     */
    public static String getResolution(IDevice iDevice) throws IDeviceExecuteShellCommandException {
        String wmSize = executeShellCommand(iDevice, "wm size");
        Pattern pattern = Pattern.compile("Physical size: (\\d+x\\d+)");
        Matcher matcher = pattern.matcher(wmSize);
        while (matcher.find()) {
            return matcher.group(1);
        }
        throw new RuntimeException("cannot find physical size, execute: wm size => " + wmSize);
    }

    public static List<String> getImeList(IDevice iDevice) throws IDeviceExecuteShellCommandException {
        String imeListString = executeShellCommand(iDevice, "ime list -s");
        if (StringUtils.isEmpty(imeListString)) {
            return Collections.emptyList();
        }
        return Arrays.asList(imeListString.split("\r\n"));
    }

    public static void setIme(IDevice iDevice, String ime) throws IDeviceExecuteShellCommandException {
        executeShellCommand(iDevice, "ime set " + ime);
    }
}

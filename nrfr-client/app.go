package main

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/electricbubble/gadb"
	wailsruntime "github.com/wailsapp/wails/v2/pkg/runtime"
)

// 版本号由 CI 通过 -ldflags "-X main.appVersion=..." 注入,
// 与根目录 gradle.properties 的 APP_VERSION_NAME 保持同步。
var appVersion = "0.0.0"

// App struct
type App struct {
	ctx            context.Context
	adbClient      gadb.Client
	selectedDevice *gadb.Device
	adbPath        string
	// weStartedADB 标记 adb server 是否由本程序启动,
	// 退出时只回收自己启动的 server,避免干扰用户其他 adb 会话。
	weStartedADB bool
}

// DeviceInfo 设备信息结构
type DeviceInfo struct {
	Serial  string `json:"serial"`
	State   string `json:"state"`
	Product string `json:"product"`
	Model   string `json:"model"`
}

// AppStatus 应用状态结构
type AppStatus struct {
	Shizuku bool `json:"shizuku"`
	Nrfr    struct {
		Installed  bool `json:"installed"`
		NeedUpdate bool `json:"needUpdate"`
	} `json:"nrfr"`
}

// NewApp creates a new App application struct
func NewApp() *App {
	return &App{}
}

// adbCmdTimeout 构造带超时控制的 adb 命令。
func (a *App) adbCmdTimeout(timeout time.Duration, args ...string) (*exec.Cmd, context.CancelFunc) {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	cmd := exec.CommandContext(ctx, a.adbPath, args...)
	hideWindow(cmd)
	return cmd, cancel
}

// startup is called when the app starts. The context is saved
func (a *App) startup(ctx context.Context) {
	a.ctx = ctx

	execPath, err := os.Executable()
	if err != nil {
		wailsruntime.LogError(ctx, fmt.Sprintf("获取执行路径失败: %v", err))
		return
	}
	execDir := filepath.Dir(execPath)

	// 优先使用程序目录下的 platform-tools
	adbPath := filepath.Join(execDir, "platform-tools", "adb")
	if runtime.GOOS == "windows" {
		adbPath = filepath.Join(execDir, "platform-tools", "adb.exe")
	}

	// 如果程序目录下的 adb 不存在,尝试使用系统环境中的 adb
	if _, err := os.Stat(adbPath); os.IsNotExist(err) {
		if runtime.GOOS == "windows" {
			androidHome := os.Getenv("ANDROID_HOME")
			if androidHome == "" {
				androidHome = filepath.Join(os.Getenv("LOCALAPPDATA"), "Android", "Sdk")
			}
			adbPath = filepath.Join(androidHome, "platform-tools", "adb.exe")
		} else {
			adbPath = "adb"
		}
	}

	a.adbPath = adbPath

	// 先用 gadb 的 socket 探测(adb devices 会隐式拉起 server,不能用来探测归属);
	// server 未运行才启动,并记录归属,退出时只回收自己启动的 server。
	probeClient, probeErr := gadb.NewClient()
	if probeErr == nil {
		if _, err := probeClient.ServerVersion(); err == nil {
			a.adbClient = probeClient
			return
		}
	}

	startCmd, cancelStart := a.adbCmdTimeout(10*time.Second, "start-server")
	if err := startCmd.Run(); err != nil {
		wailsruntime.LogError(ctx, fmt.Sprintf("启动ADB服务器失败: %v", err))
		cancelStart()
		return
	}
	cancelStart()
	a.weStartedADB = true

	client, err := gadb.NewClient()
	if err != nil {
		wailsruntime.LogError(ctx, fmt.Sprintf("初始化ADB失败: %v", err))
		return
	}
	a.adbClient = client
}

// shutdown is called when the app is closing
func (a *App) shutdown(ctx context.Context) {
	a.killADBServer()
}

// killADBServer 仅在 server 由本程序启动时关闭它
func (a *App) killADBServer() {
	if !a.weStartedADB || a.adbPath == "" {
		return
	}
	cmd, cancel := a.adbCmdTimeout(5*time.Second, "kill-server")
	defer cancel()
	if err := cmd.Run(); err != nil {
		wailsruntime.LogError(a.ctx, fmt.Sprintf("关闭ADB服务器失败: %v", err))
	}
}

// GetDevices 获取已连接的设备列表
func (a *App) GetDevices() []DeviceInfo {
	devices, err := a.adbClient.DeviceList()
	if err != nil {
		wailsruntime.LogError(a.ctx, fmt.Sprintf("获取设备列表失败: %v", err))
		return nil
	}

	var deviceInfos []DeviceInfo
	for _, device := range devices {
		state, _ := device.State()
		product, _ := device.Product()
		model, _ := device.Model()

		info := DeviceInfo{
			Serial:  device.Serial(),
			State:   string(state),
			Product: product,
			Model:   model,
		}
		deviceInfos = append(deviceInfos, info)
	}
	return deviceInfos
}

// SelectDevice 选择设备
func (a *App) SelectDevice(serial string) error {
	devices, err := a.adbClient.DeviceList()
	if err != nil {
		return fmt.Errorf("获取设备列表失败: %v", err)
	}

	for _, device := range devices {
		if device.Serial() == serial {
			a.selectedDevice = &device
			return nil
		}
	}
	return fmt.Errorf("未找到设备: %s", serial)
}

// CheckApps 检查必要的应用是否已安装
func (a *App) CheckApps() AppStatus {
	var status AppStatus
	if a.selectedDevice == nil {
		return status
	}

	status.Shizuku, _ = a.isPackageInstalled("moe.shizuku.privileged.api")

	status.Nrfr.Installed, _ = a.isPackageInstalled("com.github.nrfr")
	if status.Nrfr.Installed {
		status.Nrfr.NeedUpdate, _ = a.CheckNrfrUpdate()
	}

	return status
}

// isPackageInstalled 检查包是否已安装
func (a *App) isPackageInstalled(packageName string) (bool, error) {
	if a.selectedDevice == nil {
		return false, fmt.Errorf("未选择设备")
	}
	output, err := a.selectedDevice.RunShellCommand("pm", "list", "packages", packageName)
	if err != nil {
		return false, err
	}
	return strings.Contains(output, packageName), nil
}

// installApk 推送并安装 APK 到设备,成功后清理临时文件。
// InstallShizuku / InstallNrfr 的公共实现。
func (a *App) installApk(localPath, remotePath string) error {
	if a.selectedDevice == nil {
		return fmt.Errorf("未选择设备")
	}
	if _, err := os.Stat(localPath); err != nil {
		return fmt.Errorf("apk 文件不存在: %s", localPath)
	}

	file, err := os.Open(localPath)
	if err != nil {
		return fmt.Errorf("打开 APK 文件失败: %v", err)
	}
	defer file.Close()

	if err := a.selectedDevice.Push(file, remotePath, time.Now()); err != nil {
		return fmt.Errorf("推送 APK 文件失败: %v", err)
	}

	if _, err := a.selectedDevice.RunShellCommand("pm", "install", "-r", remotePath); err != nil {
		return fmt.Errorf("安装失败: %v", err)
	}

	if _, err := a.selectedDevice.RunShellCommand("rm", remotePath); err != nil {
		wailsruntime.LogWarning(a.ctx, fmt.Sprintf("清理临时文件失败: %v", err))
	}
	return nil
}

func (a *App) resourceApkPath(name string) (string, error) {
	execPath, err := os.Executable()
	if err != nil {
		return "", fmt.Errorf("获取执行路径失败: %v", err)
	}
	local := filepath.Join(filepath.Dir(execPath), "resources", name)
	if _, err := os.Stat(local); err != nil {
		return "", fmt.Errorf("%s 文件不存在: %s", name, local)
	}
	return local, nil
}

// InstallShizuku 安装 Shizuku
func (a *App) InstallShizuku() error {
	local, err := a.resourceApkPath("shizuku.apk")
	if err != nil {
		return err
	}
	return a.installApk(local, "/data/local/tmp/shizuku.apk")
}

// InstallNrfr 安装 Nrfr
func (a *App) InstallNrfr() error {
	local, err := a.resourceApkPath("nrfr.apk")
	if err != nil {
		return err
	}
	return a.installApk(local, "/data/local/tmp/nrfr.apk")
}

// shizukuServiceReady 轮询检测 Shizuku 服务是否已就绪
func (a *App) shizukuServiceReady() bool {
	output, err := a.selectedDevice.RunShellCommand(
		"sh", "-c", "'service list | grep -q shizuku && echo ready || echo waiting'",
	)
	if err != nil {
		return false
	}
	return strings.Contains(output, "ready")
}

// StartShizuku 启动 Shizuku
func (a *App) StartShizuku() error {
	if a.selectedDevice == nil {
		return fmt.Errorf("未选择设备")
	}

	// 先启动 Shizuku 应用
	if _, err := a.selectedDevice.RunShellCommand("monkey", "-p", "moe.shizuku.privileged.api", "1"); err != nil {
		return fmt.Errorf("启动 shizuku 应用失败: %v", err)
	}

	// 执行启动脚本(Shizuku v13.6 起脚本可位于 /data/local/tmp/shizuku,
	// 旧版仍在应用数据目录,两处依次尝试)
	startCommands := [][]string{
		{"sh", "/sdcard/Android/data/moe.shizuku.privileged.api/start.sh"},
		{"sh", "/data/local/tmp/shizuku"},
	}
	var lastErr error
	for _, cmd := range startCommands {
		output, err := a.selectedDevice.RunShellCommand(cmd[0], cmd[1:]...)
		if err == nil {
			wailsruntime.LogInfo(a.ctx, fmt.Sprintf("shizuku 启动输出: %s", output))
			lastErr = nil
			break
		}
		lastErr = err
	}
	if lastErr != nil {
		return fmt.Errorf("启动 shizuku 服务失败: %v", lastErr)
	}

	// 轮询等待服务就绪,替代固定 sleep
	for i := 0; i < 10; i++ {
		if a.shizukuServiceReady() {
			return nil
		}
		time.Sleep(500 * time.Millisecond)
	}
	return fmt.Errorf("shizuku 服务启动超时,请在设备上确认授权弹窗")
}

// WindowMinimise 最小化窗口
func (a *App) WindowMinimise() {
	wailsruntime.WindowMinimise(a.ctx)
}

// WindowMaximise 最大化窗口
func (a *App) WindowMaximise() {
	wailsruntime.WindowToggleMaximise(a.ctx)
}

// WindowClose 关闭窗口
func (a *App) WindowClose() {
	a.killADBServer()
	wailsruntime.Quit(a.ctx)
}

// StartNrfr 启动 Nrfr 应用
func (a *App) StartNrfr() error {
	if a.selectedDevice == nil {
		return fmt.Errorf("未选择设备")
	}

	if _, err := a.selectedDevice.RunShellCommand("monkey", "-p", "com.github.nrfr", "1"); err != nil {
		return fmt.Errorf("启动 nrfr 失败: %v", err)
	}
	return nil
}

// GetAppVersion 获取已安装应用的版本号
func (a *App) GetAppVersion(packageName string) (string, error) {
	if a.selectedDevice == nil {
		return "", fmt.Errorf("未选择设备")
	}

	output, err := a.selectedDevice.RunShellCommand("dumpsys", "package", packageName)
	if err != nil {
		return "", fmt.Errorf("获取版本号失败: %v", err)
	}

	for _, line := range strings.Split(output, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "versionName=") {
			return strings.TrimPrefix(line, "versionName="), nil
		}
	}
	return "", fmt.Errorf("解析版本号失败")
}

// compareVersions 比较两个版本号,如果 v1 < v2 返回 -1,v1 = v2 返回 0,v1 > v2 返回 1
func compareVersions(v1, v2 string) int {
	v1 = strings.TrimPrefix(v1, "v")
	v2 = strings.TrimPrefix(v2, "v")

	parts1 := strings.Split(v1, ".")
	parts2 := strings.Split(v2, ".")

	for len(parts1) < 3 {
		parts1 = append(parts1, "0")
	}
	for len(parts2) < 3 {
		parts2 = append(parts2, "0")
	}

	for i := 0; i < 3; i++ {
		num1, _ := strconv.Atoi(parts1[i])
		num2, _ := strconv.Atoi(parts2[i])

		if num1 < num2 {
			return -1
		}
		if num1 > num2 {
			return 1
		}
	}

	return 0
}

// CheckNrfrUpdate 检查 Nrfr 是否需要更新。
// 比较目标为打包进资源目录 nrfr.apk 的版本(与桌面工具同版发布)。
func (a *App) CheckNrfrUpdate() (bool, error) {
	if a.selectedDevice == nil {
		return false, fmt.Errorf("未选择设备")
	}

	installed, err := a.isPackageInstalled("com.github.nrfr")
	if err != nil {
		return false, err
	}
	if !installed {
		return true, nil
	}

	currentVersion, err := a.GetAppVersion("com.github.nrfr")
	if err != nil {
		return false, err
	}

	return compareVersions(currentVersion, appVersion) < 0, nil
}

// GetClientVersion 返回桌面工具自身版本,供前端"关于"展示
func (a *App) GetClientVersion() string {
	return appVersion
}

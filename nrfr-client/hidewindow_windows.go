//go:build windows

package main

import (
	"os/exec"
	"syscall"
)

const createNoWindow = 0x08000000 // CREATE_NO_WINDOW

// hideWindow 将 Windows 专有的隐藏窗口属性挂到命令上,
// 其他平台没有这个需求,由 hidewindow_other.go 提供空实现。
func hideWindow(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{
		HideWindow:    true,
		CreationFlags: createNoWindow,
	}
}

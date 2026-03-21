# AutoReconnecto

Minecraft NeoForge 自动重连模组  
Minecraft NeoForge Auto Reconnect Mod

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Languages](https://img.shields.io/badge/languages-23-yellow)](src/main/resources/assets/autoreconnect/lang)


[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.3-brightgreen)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.3.95-orange)](https://neoforged.net/)

---

## 功能 Features

- **自动重连** - 意外断开时自动尝试重连  
  Auto reconnect when disconnected unexpectedly
- **循环机制** - 每 10 秒尝试一次，直到成功  
  Retry every 10 seconds until successful
- **智能检测** - 区分主动退出和意外断开  
  Smart detection to distinguish active quit from accidental disconnect
- **指令系统** - 支持 6 条自定义指令  
  Command system with 6 customizable commands
- **多语言** - 支持 23 种语言  
  Multi-language support with 23 languages
- **图形配置** - 可通过界面或指令修改配置  
  GUI configuration available via menu or commands

## 使用 Usage

### 指令 Commands

```bash
/#autoreconnect info      # 查看模组状态
/#config <id> <value>     # 修改配置 Set config
/#config info             # 查看配置信息 View mod info
```

### 配置项 Config Options

| ID | 名称 Name | 值范围 Value Range | 默认值 Default |
|----|----------|-------------------|----------------|
| 1 | 自动重连 Auto Reconnect | true/false | true |
| 2 | 重连延迟 Reconnect Delay | 2-10 秒 seconds | 2 秒 seconds |
| 3 | 指令延迟 Command Delay | 0-60 秒 seconds | 0 秒 seconds |
| 4-9 | 指令 1-6 Commands 1-6 | 任意指令 Any command | /login 等 etc |

### 示例 Examples

```bash
/#config 1 true      # 启用自动重连 Enable auto reconnect
/#config 2 5         # 设置延迟 5 秒 Set delay to 5 seconds
/#config 4 /login    # 设置登录指令 Set login command
```

## 多语言 Multi-language

支持 23 种语言：简体中文、繁体中文、文言、日本語、한국어、English、Deutsch、Français、Español、Italiano、Polski、Português、Русский 等。  
Supports 23 languages: 简体中文，繁体中文，文言，日本語，한국어，English, Deutsch, Français, Español, Italiano, Polski, Português, Русский, and more.

## 许可证 License

MIT License

## 下载 Download

## NeoForge
| 版本 Version           | Minecraft | 下载 Download                                                                              |
|----------------------|-----------|------------------------------------------------------------------------------------------|
| 26.3.21-1.0.0-1.21.1 | 1.21.3    | [GitHub Releases](https://github.com/MCCFK/AutoReconnect-CN/releases/tag/1.21.3NeoForge) |

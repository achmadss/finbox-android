# Android Starter

A clean, modular Android project template to kickstart your next Android application. This starter template comes with a multi-module architecture and an easy setup script to customize it for your project.

## 🏗️ Architecture

This project follows a clean, modular architecture with the following modules:

- **`app`** - Main application module containing UI and application-specific code
- **`core`** - Core utilities, extensions, and shared functionality
- **`domain`** - Business logic and use cases
- **`data`** - Data sources, repositories, and data management

## 🚀 Quick Start

### Option 1: Use template and setup via workflow
1. Click the button **`"Use this template"`** and create new repository

<img width="171" height="101" alt="image" src="https://github.com/user-attachments/assets/7387fc21-1dde-4308-9824-c9340e59ddfa" />

2. Name your repository
  
3. Go to the **`"Actions"`** tab

<img width="97" height="42" alt="image" src="https://github.com/user-attachments/assets/203ece94-0762-4f20-971a-c32706ddf026" />

4. Select **`Setup Project`**

<img width="327" height="169" alt="image" src="https://github.com/user-attachments/assets/734286cd-b09e-41c4-94b6-be1ec136bbb4" />

5. Run workflow with your input

<img width="355" height="401" alt="image" src="https://github.com/user-attachments/assets/597aae9e-4bfa-4305-b935-9813603f9c0f" />

### Option 2: Manual Setup

You can run the setup script in two ways:

#### Interactive Mode
```bash
chmod +x setup.sh
./setup.sh
```

The setup script will prompt you for:

- **Project Name**: The display name of your project (e.g., "My Awesome App")
- **Package Name**: The main package name for your app module (e.g., "com.company.myapp")
- **Minimum SDK**: The minimum Android SDK version (default: 26)

**Examples:**
```bash
Project Name: (Android Starter) My Awesome App
Package Name: (com.example.androidstarter) com.company.myapp
Minimum SDK Version: (26) 24
```

#### Command Line Mode
```bash
chmod +x setup.sh
./setup.sh --name "My Awesome App" --package com.company.myapp --sdk 24 --yes
```

The script will prompt you for each configuration option.

**Command line options:**
- `-n, --name PROJECT_NAME` - Set the project name
- `-p, --package PACKAGE_NAME` - Set the package name (for app module)
- `-s, --sdk MIN_SDK` - Set the minimum SDK version
- `-y, --yes` - Skip confirmation prompt
- `-h, --help` - Show help message

**Examples:**
```bash
# Full setup with confirmation
./setup.sh --name "My App" --package com.company.myapp --sdk 26

# Quick setup without confirmation
./setup.sh -n "My App" -p com.company.myapp -s 24 -y

# Use some defaults
./setup.sh --package com.company.myapp --yes
```

This will configure your project with:
- App module package: `com.company.myapp`
- Core module package: `com.company.core`
- Domain module package: `com.company.domain`
- Data module package: `com.company.data`

## 📁 Project Structure

After setup, your project structure will look like this:

```
android-starter/
├── app/                          # Main application module
│   ├── src/main/java/your/package/name/
│   └── build.gradle
├── core/                         # Core utilities module
│   ├── src/main/java/your/package/core/
│   └── build.gradle
├── domain/                       # Business logic module
│   ├── src/main/java/your/package/domain/
│   └── build.gradle
├── data/                         # Data layer module
│   ├── src/main/java/your/package/data/
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── setup.sh
```

## 🤝 Contributing

Feel free to submit issues and pull requests to improve this starter template!

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

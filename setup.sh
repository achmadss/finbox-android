#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
DEFAULT_PROJECT_NAME="Android Starter"
DEFAULT_PACKAGE_NAME="com.example.androidstarter"
DEFAULT_MIN_SDK="26"

# Current values (what we're replacing)
CURRENT_PROJECT_NAME="Android Starter"
CURRENT_PACKAGE_NAME="com.example.androidstarter"
CURRENT_BASE_PACKAGE="com.example"

# Help function
show_help() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}    Android Starter Project Setup      ${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -n, --name PROJECT_NAME       Set the project name"
    echo "  -p, --package PACKAGE_NAME    Set the package name (for app module)"
    echo "  -s, --sdk MIN_SDK             Set the minimum SDK version"
    echo "  -y, --yes                     Skip confirmation prompt"
    echo "  -h, --help                    Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 --name \"My App\" --package com.company.myapp --sdk 24"
    echo "  $0 -n \"My App\" -p com.company.myapp -s 24 -y"
    echo ""
    echo "Interactive mode (if no arguments provided):"
    echo "  $0"
    echo ""
}

# Parse command line arguments
SKIP_CONFIRMATION=false
while [[ $# -gt 0 ]]; do
    case $1 in
        -n|--name)
            NEW_PROJECT_NAME="$2"
            shift 2
            ;;
        -p|--package)
            NEW_PACKAGE_NAME="$2"
            shift 2
            ;;
        -s|--sdk)
            NEW_MIN_SDK="$2"
            shift 2
            ;;
        -y|--yes)
            SKIP_CONFIRMATION=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            show_help
            exit 1
            ;;
    esac
done

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}    Android Starter Project Setup      ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to prompt for input with default value
prompt_with_default() {
    local prompt="$1"
    local default="$2"
    local result

    # If non-interactive (CI/CD), just return default
    if [ ! -t 0 ]; then
        echo "$default"
        return
    fi

    read -p "$(echo -e "${YELLOW}$prompt: (${BLUE}$default${YELLOW})${NC} ")" result

    if [ -z "$result" ]; then
        echo "$default"
    else
        echo "$result"
    fi
}

# Function to validate package name format
validate_package_name() {
    local package_name="$1"
    if [[ ! $package_name =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
        echo -e "${RED}Error: Invalid package name format. Use lowercase letters, numbers, underscores, and dots (e.g., com.company.appname)${NC}"
        return 1
    fi
    return 0
}

# Function to validate SDK version
validate_sdk_version() {
    local sdk_version="$1"
    if [[ ! $sdk_version =~ ^[0-9]+$ ]] || [ "$sdk_version" -lt 21 ] || [ "$sdk_version" -gt 35 ]; then
        echo -e "${RED}Error: Invalid SDK version. Must be a number between 21 and 35${NC}"
        return 1
    fi
    return 0
}

# Get user input only if not provided via CLI
echo -e "${GREEN}Please provide the following information:${NC}"
echo ""

# Project Name
if [ -z "$NEW_PROJECT_NAME" ]; then
    NEW_PROJECT_NAME=$(prompt_with_default "Project Name" "$DEFAULT_PROJECT_NAME")
fi

# Package Name with validation
if [ -z "$NEW_PACKAGE_NAME" ]; then
    while true; do
        NEW_PACKAGE_NAME=$(prompt_with_default "Package Name" "$DEFAULT_PACKAGE_NAME")
        if validate_package_name "$NEW_PACKAGE_NAME"; then
            break
        fi
    done
fi

# Extract base package (everything except the last part)
NEW_BASE_PACKAGE=$(echo "$NEW_PACKAGE_NAME" | sed 's/\.[^.]*$//')

# Minimum SDK with validation
if [ -z "$NEW_MIN_SDK" ]; then
    while true; do
        NEW_MIN_SDK=$(prompt_with_default "Minimum SDK Version" "$DEFAULT_MIN_SDK")
        if validate_sdk_version "$NEW_MIN_SDK"; then
            break
        fi
    done
fi

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}           Configuration Summary         ${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}Project Name:${NC} $NEW_PROJECT_NAME"
echo -e "${GREEN}App Package:${NC} $NEW_PACKAGE_NAME"
echo -e "${GREEN}Base Package:${NC} $NEW_BASE_PACKAGE"
echo -e "${GREEN}Core Package:${NC} $NEW_BASE_PACKAGE.core"
echo -e "${GREEN}Domain Package:${NC} $NEW_BASE_PACKAGE.domain"
echo -e "${GREEN}Data Package:${NC} $NEW_BASE_PACKAGE.data"
echo -e "${GREEN}Minimum SDK:${NC} $NEW_MIN_SDK"
echo ""

if [ "$SKIP_CONFIRMATION" = false ] && [ -t 0 ]; then
    read -p "Do you want to proceed with these changes? (y/N): " confirm
    if [[ ! $confirm =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}Setup cancelled.${NC}"
        exit 0
    fi
else
    echo -e "${GREEN}Proceeding automatically (--yes flag used or non-interactive)...${NC}"
fi

echo ""
echo -e "${GREEN}Starting project setup...${NC}"

# Sanitize project names for use in theme names (remove spaces)
SANITIZED_CURRENT_PROJECT_NAME=$(echo "$CURRENT_PROJECT_NAME" | sed 's/ //g')
SANITIZED_NEW_PROJECT_NAME=$(echo "$NEW_PROJECT_NAME" | sed 's/ //g')

# Function to update files with sed (cross-platform compatible)
update_file() {
    local file="$1"
    local search="$2"
    local replace="$3"

    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        sed -i '' "s|$search|$replace|g" "$file"
    else
        # Linux and others
        sed -i "s|$search|$replace|g" "$file"
    fi
}

# Function to update package declarations in source files
update_package_in_file() {
    local file="$1"
    local old_package="$2"
    local new_package="$3"

    echo "  Updating package in: $file"
    update_file "$file" "package $old_package" "package $new_package"
}

# Function to update import statements
update_imports_in_file() {
    local file="$1"
    local old_base="$2"
    local new_base="$3"

    echo "  Updating imports in: $file"
    update_file "$file" "import $old_base" "import $new_base"
    update_file "$file" "import $old_base." "import $new_base."
}

# Function to clean up unused package directories
cleanup_unused_directories() {
    local module="$1"
    local new_base_package="$2"

    echo -e "${BLUE}Cleaning up unused directories in $module module...${NC}"

    # Define source directories
    local src_main="$module/src/main/java"
    local src_test="$module/src/test/java"
    local src_android_test="$module/src/androidTest/java"

    # If new package doesn't start with 'com', remove empty 'com' directories
    if [[ ! "$new_base_package" == com.* ]]; then
        for src_dir in "$src_main" "$src_test" "$src_android_test"; do
            if [ -d "$src_dir" ]; then
                local com_dir="$src_dir/com"
                if [ -d "$com_dir" ]; then
                    # Check if com directory is empty (recursively)
                    if [ -z "$(find "$com_dir" -name "*.kt" -o -name "*.java" 2>/dev/null)" ]; then
                        echo "  Removing empty com directory: $com_dir"
                        rm -rf "$com_dir"
                    else
                        echo "  Keeping com directory (contains source files): $com_dir"
                    fi
                fi
            fi
        done
    fi

    # Similarly, if new package doesn't start with other common prefixes, clean those up too
    # This handles cases where someone might switch from other base packages
    local old_base_parts=($(echo "$CURRENT_BASE_PACKAGE" | tr '.' ' '))
    local new_base_parts=($(echo "$new_base_package" | tr '.' ' '))

    # If the first part of the package changed (e.g., com -> dev), clean up the old one
    if [ "${old_base_parts[0]}" != "${new_base_parts[0]}" ]; then
        for src_dir in "$src_main" "$src_test" "$src_android_test"; do
            if [ -d "$src_dir" ]; then
                local old_root_dir="$src_dir/${old_base_parts[0]}"
                if [ -d "$old_root_dir" ]; then
                    # Check if the old root directory is empty (recursively)
                    if [ -z "$(find "$old_root_dir" -name "*.kt" -o -name "*.java" 2>/dev/null)" ]; then
                        echo "  Removing empty ${old_base_parts[0]} directory: $old_root_dir"
                        rm -rf "$old_root_dir"
                    else
                        echo "  Keeping ${old_base_parts[0]} directory (contains source files): $old_root_dir"
                    fi
                fi
            fi
        done
    fi
}
reorganize_source_files() {
    local module="$1"
    local old_package_path="$2"
    local new_package_path="$3"

    echo -e "${BLUE}Reorganizing source files for $module module...${NC}"

    # Define source directories
    local src_main="$module/src/main/java"
    local src_test="$module/src/test/java"
    local src_android_test="$module/src/androidTest/java"

    # Process each source directory if it exists
    for src_dir in "$src_main" "$src_test" "$src_android_test"; do
        if [ -d "$src_dir" ]; then
            local old_dir="$src_dir/$old_package_path"
            local new_dir="$src_dir/$new_package_path"

            if [ -d "$old_dir" ]; then
                echo "  Processing $old_dir"

                # Create new directory structure
                mkdir -p "$new_dir"

                # Move all files and subdirectories from old to new location
                find "$old_dir" -mindepth 1 -maxdepth 1 | while read -r item; do
                    if [ -e "$item" ]; then
                        echo "    Moving $(basename "$item") to $new_dir/"
                        mv "$item" "$new_dir/"
                    fi
                done

                # Remove the old directory if it's empty
                if [ -d "$old_dir" ] && [ -z "$(ls -A "$old_dir")" ]; then
                    rmdir "$old_dir"
                    echo "    Removed empty directory: $old_dir"
                fi

                # Clean up empty parent directories
                local parent_dir="$(dirname "$old_dir")"
                while [ "$parent_dir" != "$src_dir" ] && [ -d "$parent_dir" ] && [ -z "$(ls -A "$parent_dir")" ]; do
                    echo "    Removing empty parent directory: $parent_dir"
                    rmdir "$parent_dir"
                    parent_dir="$(dirname "$parent_dir")"
                done
            else
                echo "  Directory $old_dir does not exist, skipping..."
            fi
        fi
    done
}

# 1. Update settings.gradle or settings.gradle.kts
echo -e "${BLUE}1. Updating project name in settings files...${NC}"
if [ -f "settings.gradle" ]; then
    echo "  Updating settings.gradle"
    update_file "settings.gradle" "rootProject.name = \"$CURRENT_PROJECT_NAME\"" "rootProject.name = \"$NEW_PROJECT_NAME\""
fi

if [ -f "settings.gradle.kts" ]; then
    echo "  Updating settings.gradle.kts"
    update_file "settings.gradle.kts" "rootProject.name = \"$CURRENT_PROJECT_NAME\"" "rootProject.name = \"$NEW_PROJECT_NAME\""
fi

# 2. Update build.gradle files for minimum SDK
echo -e "${BLUE}2. Updating minimum SDK version...${NC}"
for module in "app" "core" "domain" "data"; do
    if [ -f "$module/build.gradle" ]; then
        echo "  Updating $module/build.gradle"
        update_file "$module/build.gradle" "minSdk $DEFAULT_MIN_SDK" "minSdk $NEW_MIN_SDK"
        update_file "$module/build.gradle" "minSdkVersion $DEFAULT_MIN_SDK" "minSdkVersion $NEW_MIN_SDK"
    fi

    if [ -f "$module/build.gradle.kts" ]; then
        echo "  Updating $module/build.gradle.kts"
        update_file "$module/build.gradle.kts" "minSdk = $DEFAULT_MIN_SDK" "minSdk = $NEW_MIN_SDK"
        update_file "$module/build.gradle.kts" "minSdkVersion($DEFAULT_MIN_SDK)" "minSdkVersion($NEW_MIN_SDK)"
    fi
done

# 3. Update package names in build.gradle files
echo -e "${BLUE}3. Updating package names in build files...${NC}"
# App module
for gradle_file in "app/build.gradle" "app/build.gradle.kts"; do
    if [ -f "$gradle_file" ]; then
        echo "  Updating $gradle_file"
        # For build.gradle (Groovy)
        update_file "$gradle_file" "applicationId \"$CURRENT_PACKAGE_NAME\"" "applicationId \"$NEW_PACKAGE_NAME\""
        update_file "$gradle_file" "namespace \"$CURRENT_PACKAGE_NAME\"" "namespace \"$NEW_PACKAGE_NAME\""

        # For build.gradle.kts (Kotlin)
        update_file "$gradle_file" "applicationId = \"$CURRENT_PACKAGE_NAME\"" "applicationId = \"$NEW_PACKAGE_NAME\""
        update_file "$gradle_file" "namespace = \"$CURRENT_PACKAGE_NAME\"" "namespace = \"$NEW_PACKAGE_NAME\""
    fi
done

# Other modules (core, domain, data)
for module in "core" "domain" "data"; do
    for gradle_file in "$module/build.gradle" "$module/build.gradle.kts"; do
        if [ -f "$gradle_file" ]; then
            echo "  Updating $gradle_file"
            update_file "$gradle_file" "namespace \"$CURRENT_BASE_PACKAGE.$module\"" "namespace \"$NEW_BASE_PACKAGE.$module\""
            update_file "$gradle_file" "namespace = \"$CURRENT_BASE_PACKAGE.$module\"" "namespace = \"$NEW_BASE_PACKAGE.$module\""
        fi
    done
done

# 4. Update AndroidManifest.xml files
echo -e "${BLUE}4. Updating AndroidManifest.xml files...${NC}"
if [ -f "app/src/main/AndroidManifest.xml" ]; then
    echo "  Updating app/src/main/AndroidManifest.xml"
    update_file "app/src/main/AndroidManifest.xml" "package=\"$CURRENT_PACKAGE_NAME\"" "package=\"$NEW_PACKAGE_NAME\""
    update_file "app/src/main/AndroidManifest.xml" "android:theme=\"@style/Theme.$SANITIZED_CURRENT_PROJECT_NAME\"" "android:theme=\"@style/Theme.$SANITIZED_NEW_PROJECT_NAME\""
fi

# 5. Update source files and reorganize directory structure
echo -e "${BLUE}5. Updating source files and reorganizing directories...${NC}"

# Convert package names to directory paths
CURRENT_APP_PATH=$(echo "$CURRENT_PACKAGE_NAME" | tr '.' '/')
NEW_APP_PATH=$(echo "$NEW_PACKAGE_NAME" | tr '.' '/')
CURRENT_BASE_PATH=$(echo "$CURRENT_BASE_PACKAGE" | tr '.' '/')
NEW_BASE_PATH=$(echo "$NEW_BASE_PACKAGE" | tr '.' '/')

# Handle app module
if [ -d "app" ]; then
    echo -e "${YELLOW}Processing app module...${NC}"

    # First, let's find what the actual current app package is by looking at the manifest
    ACTUAL_CURRENT_PACKAGE=""
    if [ -f "app/src/main/AndroidManifest.xml" ]; then
        ACTUAL_CURRENT_PACKAGE=$(grep -o 'package="[^"]*"' app/src/main/AndroidManifest.xml | sed 's/package="//;s/"//')
        echo "  Detected current app package: $ACTUAL_CURRENT_PACKAGE"
    fi

    # Use the detected package or fall back to the default
    PACKAGE_TO_REPLACE="$ACTUAL_CURRENT_PACKAGE"
    if [ -z "$PACKAGE_TO_REPLACE" ]; then
        PACKAGE_TO_REPLACE="$CURRENT_PACKAGE_NAME"
    fi

    # Update package declarations and imports in all source files
    find app/src -name "*.kt" -o -name "*.java" | while read -r file; do
        echo "  Processing file: $file"

        # Show what imports currently exist in the file for debugging
        if grep -q "import.*$PACKAGE_TO_REPLACE" "$file" 2>/dev/null; then
            echo "    Found imports to update:"
            grep "import.*$PACKAGE_TO_REPLACE" "$file" | head -5
        fi

        # Update package declarations
        if grep -q "^package $PACKAGE_TO_REPLACE" "$file" 2>/dev/null; then
            echo "    Updating package declaration: $PACKAGE_TO_REPLACE -> $NEW_PACKAGE_NAME"
            update_file "$file" "^package $PACKAGE_TO_REPLACE" "package $NEW_PACKAGE_NAME"
        fi

        if grep -q "^package $PACKAGE_TO_REPLACE\." "$file" 2>/dev/null; then
            echo "    Updating sub-package declaration"
            update_file "$file" "^package $PACKAGE_TO_REPLACE\." "package $NEW_PACKAGE_NAME."
        fi

        # Update import statements - handle both exact package and sub-packages
        if [ -n "$PACKAGE_TO_REPLACE" ]; then
            # Replace imports that start with the old app package
            if grep -q "import $PACKAGE_TO_REPLACE" "$file" 2>/dev/null; then
                echo "    Updating imports: $PACKAGE_TO_REPLACE -> $NEW_PACKAGE_NAME"
                # Use a more explicit sed command
                if [[ "$OSTYPE" == "darwin"* ]]; then
                    # macOS
                    sed -i '' "s|import $PACKAGE_TO_REPLACE|import $NEW_PACKAGE_NAME|g" "$file"
                    sed -i '' "s|import $PACKAGE_TO_REPLACE\.|import $NEW_PACKAGE_NAME.|g" "$file"
                else
                    # Linux
                    sed -i "s|import $PACKAGE_TO_REPLACE|import $NEW_PACKAGE_NAME|g" "$file"
                    sed -i "s|import $PACKAGE_TO_REPLACE\.|import $NEW_PACKAGE_NAME.|g" "$file"
                fi
            fi
        fi

        # Update imports for other modules (core, domain, data) if they exist
        if grep -q "import $CURRENT_BASE_PACKAGE" "$file" 2>/dev/null; then
            echo "    Updating module imports: $CURRENT_BASE_PACKAGE -> $NEW_BASE_PACKAGE"
            update_imports_in_file "$file" "$CURRENT_BASE_PACKAGE" "$NEW_BASE_PACKAGE"
        fi

        # Show what the file looks like after updates for debugging
        echo "    After update, imports are:"
        grep "^import" "$file" | head -5 || echo "    No imports found"
        echo ""
    done

    # Reorganize directory structure using the actual detected package
    if [ -n "$ACTUAL_CURRENT_PACKAGE" ]; then
        ACTUAL_CURRENT_PATH=$(echo "$ACTUAL_CURRENT_PACKAGE" | tr '.' '/')
        reorganize_source_files "app" "$ACTUAL_CURRENT_PATH" "$NEW_APP_PATH"
    else
        reorganize_source_files "app" "$CURRENT_APP_PATH" "$NEW_APP_PATH"
    fi

    # Clean up unused directories
    cleanup_unused_directories "app" "$NEW_BASE_PACKAGE"
fi

# Handle other modules (core, domain, data)
for module in "core" "domain" "data"; do
    if [ -d "$module" ]; then
        echo -e "${YELLOW}Processing $module module...${NC}"

        current_module_package="$CURRENT_BASE_PACKAGE.$module"
        new_module_package="$NEW_BASE_PACKAGE.$module"
        current_module_path="$CURRENT_BASE_PATH/$module"
        new_module_path="$NEW_BASE_PATH/$module"

        # Update package declarations and imports in all source files
        find "$module/src" -name "*.kt" -o -name "*.java" 2>/dev/null | while read -r file; do
            if grep -q "package $current_module_package" "$file" 2>/dev/null; then
                update_package_in_file "$file" "$current_module_package" "$new_module_package"
            fi
            # Update any imports that reference the old base package
            update_imports_in_file "$file" "$CURRENT_BASE_PACKAGE" "$NEW_BASE_PACKAGE"
        done

        # Reorganize directory structure
        reorganize_source_files "$module" "$current_module_path" "$new_module_path"

        # Clean up unused directories
        cleanup_unused_directories "$module" "$NEW_BASE_PACKAGE"
    fi
done

# 6. Update any remaining references in resource files
echo -e "${BLUE}6. Updating resource files...${NC}"
find . -name "*.xml" -not -path "./build/*" -not -path "./.git/*" | while read -r file; do
    if grep -q "$CURRENT_BASE_PACKAGE" "$file" 2>/dev/null; then
        echo "  Updating references in: $file"
        update_file "$file" "$CURRENT_BASE_PACKAGE" "$NEW_BASE_PACKAGE"
        update_file "$file" "$CURRENT_PACKAGE_NAME" "$NEW_PACKAGE_NAME"
    fi
done

# 7. Update proguard files if they exist
echo -e "${BLUE}7. Updating ProGuard files...${NC}"
find . -name "proguard-*.pro" -o -name "consumer-rules.pro" | while read -r file; do
    if [ -f "$file" ] && grep -q "$CURRENT_BASE_PACKAGE" "$file" 2>/dev/null; then
        echo "  Updating $file"
        update_file "$file" "$CURRENT_BASE_PACKAGE" "$NEW_BASE_PACKAGE"
    fi
done

# 8. Update UI-related resources (strings.xml, themes.xml)
echo -e "${BLUE}8. Updating UI-related resources...${NC}"
# Update app_name in strings.xml
if [ -f "app/src/main/res/values/strings.xml" ]; then
    echo "  Updating app_name in app/src/main/res/values/strings.xml"
    update_file "app/src/main/res/values/strings.xml" ">$CURRENT_PROJECT_NAME<" ">$NEW_PROJECT_NAME<"
fi

# Update theme name in themes.xml files
for theme_file in "app/src/main/res/values/themes.xml" "app/src/main/res/values-night/themes.xml"; do
    if [ -f "$theme_file" ]; then
        echo "  Updating theme name in $theme_file"
        update_file "$theme_file" "name=\"Theme.$SANITIZED_CURRENT_PROJECT_NAME\"" "name=\"Theme.$SANITIZED_NEW_PROJECT_NAME\""
        update_file "$theme_file" "parent=\"Theme.$SANITIZED_CURRENT_PROJECT_NAME\"" "parent=\"Theme.$SANITIZED_NEW_PROJECT_NAME\""
    fi
done


echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}         Setup Complete!                ${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${GREEN}Changes made:${NC}"
echo "✓ Project name updated to: $NEW_PROJECT_NAME"
echo "✓ App name in strings.xml updated"
echo "✓ App theme name updated to: Theme.$SANITIZED_NEW_PROJECT_NAME"
echo "✓ App package and applicationId updated to: $NEW_PACKAGE_NAME"
echo "✓ Core module package updated to: $NEW_BASE_PACKAGE.core"
echo "✓ Domain module package updated to: $NEW_BASE_PACKAGE.domain"
echo "✓ Data module package updated to: $NEW_BASE_PACKAGE.data"
echo "✓ Minimum SDK updated to: $NEW_MIN_SDK"
echo "✓ Source files reorganized and updated"
echo "✓ Import statements updated"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "Open in Android Studio"
echo ""
echo -e "${BLUE}Happy coding! 🚀${NC}"
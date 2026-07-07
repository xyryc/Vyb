#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# ANSI Color Codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}===================================================================${NC}"
echo -e "${BLUE}        Android Release Bundle/APK Signing & Verification tool      ${NC}"
echo -e "${BLUE}===================================================================${NC}"

# Default parameters
KEYSTORE="my-upload-key.jks"
TARGET_FILE=""
STOREPASS=""
KEYPASS=""
ALIAS=""

# Help function
show_help() {
    echo -e "Usage: ./sign_and_verify.sh [OPTIONS]"
    echo -e ""
    echo -e "Options:"
    echo -e "  --keystore <path>      Path to the keystore JKS file (default: my-upload-key.jks)"
    echo -e "  --target <path>        Path to the unsigned/signed .aab or .apk file"
    echo -e "  --storepass <password> Keystore password"
    echo -e "  --keypass <password>   Private key password (defaults to keystore password)"
    echo -e "  --alias <alias>        Key alias (auto-detected if keystore has only 1 alias)"
    echo -e "  -h, --help             Show this help message"
    echo -e ""
    echo -e "Example:"
    echo -e "  ./sign_and_verify.sh --target app/build/outputs/bundle/release/app-release.aab"
}

# Parse options
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --keystore) KEYSTORE="$2"; shift ;;
        --target) TARGET_FILE="$2"; shift ;;
        --storepass) STOREPASS="$2"; shift ;;
        --keypass) KEYPASS="$2"; shift ;;
        --alias) ALIAS="$2"; shift ;;
        -h|--help) show_help; exit 0 ;;
        *) echo -e "${RED}Error: Unknown parameter: $1${NC}"; show_help; exit 1 ;;
    esac
    shift
done

# Check if keystore exists
if [ ! -f "$KEYSTORE" ]; then
    echo -e "${RED}Error: Keystore file '${KEYSTORE}' not found!${NC}"
    echo -e "Please place your '${KEYSTORE}' in the root directory or specify its path with --keystore <path>."
    exit 1
fi

# Auto-detect target file if not specified
if [ -z "$TARGET_FILE" ]; then
    echo -e "${YELLOW}No target file specified. Searching for built bundles (.aab) or packages (.apk)...${NC}"
    # Search in .build-outputs, app/build/outputs, etc.
    FOUND_FILES=$(find . -name "*.aab" -o -name "*release*.apk" -not -path "*/intermediates/*" -not -path "*/tmp/*" | grep -v "unaligned" || true)
    
    if [ -z "$FOUND_FILES" ]; then
        echo -e "${RED}Error: No built .aab or release .apk files found in the project.${NC}"
        echo -e "Please build your app first (e.g., run 'gradle bundleRelease' or 'gradle assembleRelease') or specify --target <path>."
        exit 1
    fi
    
    # Let user select if multiple files are found
    FILE_COUNT=$(echo "$FOUND_FILES" | wc -l)
    if [ "$FILE_COUNT" -eq 1 ]; then
        TARGET_FILE=$(echo "$FOUND_FILES" | xargs)
        echo -e "${GREEN}Found unique target file: ${CYAN}${TARGET_FILE}${NC}"
    else
        echo -e "${YELLOW}Multiple potential target files found:${NC}"
        IFS=$'\n' read -rd '' -a FILE_ARRAY <<< "$FOUND_FILES"
        for i in "${!FILE_ARRAY[@]}"; do
            echo -e "  [$i] ${FILE_ARRAY[$i]}"
        done
        echo -n "Select target file index [0-$((${#FILE_ARRAY[@]} - 1))]: "
        read -r SELECT_IDX
        if [[ "$SELECT_IDX" =~ ^[0-9]+$ ]] && [ "$SELECT_IDX" -lt "${#FILE_ARRAY[@]}" ]; then
            TARGET_FILE="${FILE_ARRAY[$SELECT_IDX]}"
        else
            echo -e "${RED}Invalid selection. Exiting.${NC}"
            exit 1
        fi
    fi
fi

if [ ! -f "$TARGET_FILE" ]; then
    echo -e "${RED}Error: Target file '${TARGET_FILE}' not found!${NC}"
    exit 1
fi

# Prompt for passwords if not provided
if [ -z "$STOREPASS" ]; then
    echo -n "Enter password for keystore '${KEYSTORE}': "
    read -rs STOREPASS
    echo ""
fi

if [ -z "$KEYPASS" ]; then
    echo -n "Enter private key password (press ENTER to use keystore password): "
    read -rs KEYPASS
    echo ""
    if [ -z "$KEYPASS" ]; then
        KEYPASS="$STOREPASS"
    fi
fi

# Validate keystore password and list aliases
echo -e "${YELLOW}Reading keystore information...${NC}"
set +e
ALIASES_OUTPUT=$(keytool -list -keystore "$KEYSTORE" -storepass "$STOREPASS" 2>&1)
EXIT_CODE=$?
set -e

if [ $EXIT_CODE -ne 0 ]; then
    echo -e "${RED}Error: Failed to access keystore with the provided password.${NC}"
    echo -e "${RED}$ALIASES_OUTPUT${NC}"
    exit 1
fi

# Auto-detect alias if not specified
if [ -z "$ALIAS" ]; then
    # Find all lines containing privateKeyEntry or privateKey
    DETECTED_ALIASES=$(echo "$ALIASES_OUTPUT" | grep "PrivateKeyEntry" | cut -d',' -f1 | xargs || true)
    
    if [ -z "$DETECTED_ALIASES" ]; then
        # Check standard PKCS12 / JKS format
        DETECTED_ALIASES=$(echo "$ALIASES_OUTPUT" | grep -E '^[a-zA-Z0-9_-]+,' | cut -d',' -f1 | xargs || true)
    fi
    
    ALIAS_COUNT=$(echo "$DETECTED_ALIASES" | wc -w)
    
    if [ "$ALIAS_COUNT" -eq 1 ]; then
        ALIAS=$(echo "$DETECTED_ALIASES" | xargs)
        echo -e "${GREEN}Automatically detected alias: ${CYAN}${ALIAS}${NC}"
    elif [ "$ALIAS_COUNT" -gt 1 ]; then
        echo -e "${YELLOW}Multiple aliases found in keystore:${NC}"
        for entry in $DETECTED_ALIASES; do
            echo -e "  - $entry"
        done
        echo -n "Enter the alias to use: "
        read -r ALIAS
    else
        echo -e "${RED}Error: No private key entries found in keystore!${NC}"
        exit 1
    fi
fi

# Extract signature certificate fingerprint from Keystore
echo -e "${YELLOW}Extracting certificate fingerprint from Keystore key...${NC}"
set +e
KEYSTORE_SHA256=$(keytool -list -v -keystore "$KEYSTORE" -storepass "$STOREPASS" -alias "$ALIAS" 2>/dev/null | grep -i "SHA256:" | head -n 1 | cut -d':' -f2- | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')
KEYSTORE_SHA1=$(keytool -list -v -keystore "$KEYSTORE" -storepass "$STOREPASS" -alias "$ALIAS" 2>/dev/null | grep -i "SHA1:" | head -n 1 | cut -d':' -f2- | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')
set -e

if [ -z "$KEYSTORE_SHA256" ]; then
    echo -e "${RED}Error: Could not extract certificate fingerprints for alias '${ALIAS}' in keystore '${KEYSTORE}'.${NC}"
    exit 1
fi

# Determine type (APK or AAB)
FILE_EXT="${TARGET_FILE##*.}"
FILE_EXT=$(echo "$FILE_EXT" | tr '[:upper:]' '[:lower:]')

echo -e "\n${YELLOW}Signing target ${PURPLE}${FILE_EXT^^}${NC}: ${CYAN}${TARGET_FILE}${NC}..."

if [ "$FILE_EXT" = "aab" ]; then
    # Sign AAB using jarsigner
    set +e
    SIGN_OUTPUT=$(jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
        -keystore "$KEYSTORE" \
        -storepass "$STOREPASS" \
        -keypass "$KEYPASS" \
        "$TARGET_FILE" "$ALIAS" 2>&1)
    SIGN_STATUS=$?
    set -e
    
    if [ $SIGN_STATUS -ne 0 ]; then
        echo -e "${RED}Error: jarsigner failed to sign the AAB!${NC}"
        echo -e "${RED}$SIGN_OUTPUT${NC}"
        exit 1
    fi
    echo -e "${GREEN}AAB signed successfully with jarsigner.${NC}"
    
elif [ "$FILE_EXT" = "apk" ]; then
    # Try finding apksigner tool
    APKSIGNER_PATH=$(find "$ANDROID_SDK_ROOT/build-tools" -name "apksigner" -type f | sort -V | tail -n 1 2>/dev/null || true)
    if [ -z "$APKSIGNER_PATH" ]; then
        APKSIGNER_PATH=$(which apksigner 2>/dev/null || true)
    fi
    
    if [ -n "$APKSIGNER_PATH" ]; then
        echo -e "Using apksigner: ${CYAN}$APKSIGNER_PATH${NC}"
        set +e
        SIGN_OUTPUT=$("$APKSIGNER_PATH" sign --ks "$KEYSTORE" --ks-pass "pass:$STOREPASS" --key-pass "pass:$KEYPASS" --ks-key-alias "$ALIAS" "$TARGET_FILE" 2>&1)
        SIGN_STATUS=$?
        set -e
        
        if [ $SIGN_STATUS -ne 0 ]; then
            echo -e "${RED}Error: apksigner failed to sign the APK!${NC}"
            echo -e "${RED}$SIGN_OUTPUT${NC}"
            exit 1
        fi
        echo -e "${GREEN}APK signed successfully with apksigner.${NC}"
    else
        echo -e "${YELLOW}Warning: apksigner not found in SDK. Falling back to jarsigner...${NC}"
        set +e
        SIGN_OUTPUT=$(jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
            -keystore "$KEYSTORE" \
            -storepass "$STOREPASS" \
            -keypass "$KEYPASS" \
            "$TARGET_FILE" "$ALIAS" 2>&1)
        SIGN_STATUS=$?
        set -e
        
        if [ $SIGN_STATUS -ne 0 ]; then
            echo -e "${RED}Error: jarsigner failed to sign the APK!${NC}"
            echo -e "${RED}$SIGN_OUTPUT${NC}"
            exit 1
        fi
        echo -e "${GREEN}APK signed successfully with jarsigner.${NC}"
    fi
else
    echo -e "${RED}Error: Unsupported file format '.${FILE_EXT}'. Only .aab and .apk are supported.${NC}"
    exit 1
fi

# Verify the file
echo -e "\n${YELLOW}Verifying signature on signed file...${NC}"

if [ "$FILE_EXT" = "aab" ]; then
    set +e
    VERIFY_OUTPUT=$(jarsigner -verify -verbose -certs "$TARGET_FILE" 2>&1)
    VERIFY_STATUS=$?
    set -e
    
    if [ $VERIFY_STATUS -ne 0 ]; then
        echo -e "${RED}Error: AAB signature verification failed!${NC}"
        echo -e "${RED}$VERIFY_OUTPUT${NC}"
        exit 1
    fi
else
    APKSIGNER_PATH=$(find "$ANDROID_SDK_ROOT/build-tools" -name "apksigner" -type f | sort -V | tail -n 1 2>/dev/null || true)
    if [ -z "$APKSIGNER_PATH" ]; then
        APKSIGNER_PATH=$(which apksigner 2>/dev/null || true)
    fi
    
    if [ -n "$APKSIGNER_PATH" ]; then
        set +e
        VERIFY_OUTPUT=$("$APKSIGNER_PATH" verify --verbose "$TARGET_FILE" 2>&1)
        VERIFY_STATUS=$?
        set -e
        
        if [ $VERIFY_STATUS -ne 0 ]; then
            echo -e "${RED}Error: APK signature verification failed!${NC}"
            echo -e "${RED}$VERIFY_OUTPUT${NC}"
            exit 1
        fi
    else
        set +e
        VERIFY_OUTPUT=$(jarsigner -verify -verbose "$TARGET_FILE" 2>&1)
        VERIFY_STATUS=$?
        set -e
        
        if [ $VERIFY_STATUS -ne 0 ]; then
            echo -e "${RED}Error: APK signature verification failed!${NC}"
            echo -e "${RED}$VERIFY_OUTPUT${NC}"
            exit 1
        fi
    fi
fi

# Extracting signature certificate fingerprint from the signed package
echo -e "${YELLOW}Extracting certificate fingerprint from signed package...${NC}"
set +e
PACKAGE_SHA256=$(keytool -printcert -jarfile "$TARGET_FILE" 2>/dev/null | grep -i "SHA256:" | head -n 1 | cut -d':' -f2- | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')
PACKAGE_SHA1=$(keytool -printcert -jarfile "$TARGET_FILE" 2>/dev/null | grep -i "SHA1:" | head -n 1 | cut -d':' -f2- | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')
set -e

# Print comparison
echo -e "\n${BLUE}===================================================================${NC}"
echo -e "${BLUE}                    SIGNATURE COMPARISON SUMMARY                   ${NC}"
echo -e "${BLUE}===================================================================${NC}"

echo -e "Keystore Key SHA-256:  ${CYAN}${KEYSTORE_SHA256}${NC}"
echo -e "Signed File SHA-256:   ${CYAN}${PACKAGE_SHA256}${NC}"
echo -e "-------------------------------------------------------------------"
echo -e "Keystore Key SHA-1:    ${PURPLE}${KEYSTORE_SHA1}${NC}"
echo -e "Signed File SHA-1:     ${PURPLE}${PACKAGE_SHA1}${NC}"
echo -e "==================================================================="

if [ "$KEYSTORE_SHA256" = "$PACKAGE_SHA256" ] && [ -n "$PACKAGE_SHA256" ]; then
    echo -e "${GREEN}✔ SUCCESS: Certificate signatures match perfectly!${NC}"
    echo -e "${GREEN}✔ The bundle/APK is correctly signed and ready for release/Play Store!${NC}"
else
    echo -e "${RED}✘ ERROR: Certificate signatures DO NOT match!${NC}"
    echo -e "${RED}Please verify that the target was signed with the correct key and password.${NC}"
    exit 1
fi

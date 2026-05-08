# Getting gradle-wrapper.jar

The `gradle/wrapper/gradle-wrapper.jar` file cannot be included in this repository.
Get it in one of these ways:

## Option 1: Extract from a downloaded Gradle zip (easiest)

Download `gradle-9.4.1-bin.zip` from https://gradle.org/releases/, then:

```powershell
# PowerShell — adjust paths to match where you saved the zip
Expand-Archive <path-to-gradle-9.4.1-bin.zip> -DestinationPath <temp-dir>
Copy-Item <temp-dir>\gradle-9.4.1\lib\plugins\gradle-wrapper-*.jar gradle\wrapper\gradle-wrapper.jar
```

```bash
# Bash / macOS / Linux
unzip gradle-9.4.1-bin.zip -d /tmp/gradle-tmp
cp /tmp/gradle-tmp/gradle-9.4.1/lib/plugins/gradle-wrapper-*.jar gradle/wrapper/gradle-wrapper.jar
```

## Option 2: Use system gradle to generate the wrapper

If `gradle` is in your PATH:
```
gradle wrapper --gradle-version 9.4.1
```
This generates both `gradlew` / `gradlew.bat` and `gradle-wrapper.jar` automatically.

## Option 3: Download directly

From: https://services.gradle.org/distributions/
Or search Maven Central for: `gradle-wrapper`

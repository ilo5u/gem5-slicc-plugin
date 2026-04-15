# Getting gradle-wrapper.jar

The `gradle/wrapper/gradle-wrapper.jar` file cannot be included in this zip
because it requires downloading. Get it in one of these ways:

## Option 1: Extract from your downloaded Gradle zip (easiest)

You already have `gradle-9.4.1-bin.zip`. Extract it and copy the jar:

```
# In PowerShell:
Expand-Archive C:\Users\chengxin\Downloads\gradle-9.4.1-bin.zip -DestinationPath C:\gradle-tmp
copy C:\gradle-tmp\gradle-9.4.1\lib\plugins\gradle-wrapper-*.jar gradle\wrapper\gradle-wrapper.jar
```

## Option 2: Use system gradle to generate the wrapper

If gradle is in your PATH:
```
gradle wrapper --gradle-version 9.4.1
```
This will generate both `gradlew.bat` and `gradle-wrapper.jar` automatically.

## Option 3: Download directly

From: https://services.gradle.org/distributions/
Or search Maven Central for: `gradle-wrapper`

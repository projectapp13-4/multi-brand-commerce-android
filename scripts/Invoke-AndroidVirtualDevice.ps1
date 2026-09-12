[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet(
        'Preflight',
        'Status',
        'Start',
        'Wait',
        'Stop',
        'Install',
        'Launch',
        'StopApp',
        'RestartApp',
        'Screenshot',
        'Hierarchy',
        'Tap',
        'Swipe',
        'Text',
        'Key',
        'DeepLink',
        'Logcat',
        'Crashes'
    )]
    [string]$Action,

    [string]$AvdName = 'GurBakir_API36',
    [string]$Serial = 'emulator-5554',
    [string]$PackageName = 'com.gurbakir.mobile.dev.debug',
    [string]$ApkPath,
    [string]$OutputPath,
    [string]$TextValue,
    [string]$Uri,
    [int]$X,
    [int]$Y,
    [int]$EndX,
    [int]$EndY,
    [ValidateRange(100, 10000)]
    [int]$DurationMs = 350,
    [string]$KeyCode = 'KEYCODE_BACK',
    [ValidateRange(30, 900)]
    [int]$TimeoutSeconds = 300,
    [switch]$Windowed,
    [switch]$ColdBoot,
    [switch]$WipeData
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
    $env:ANDROID_SDK_ROOT
} elseif (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
    $env:ANDROID_HOME
} else {
    'C:\src\android-sdk'
}
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $sdkRoot 'emulator\emulator.exe'
$emulatorCheck = Join-Path $sdkRoot 'emulator\emulator-check.exe'
$defaultApk = Join-Path $repoRoot 'app\build\outputs\apk\development\debug\app-development-debug.apk'
$evidenceRoot = Join-Path $repoRoot 'build\android-virtual-device'

function Assert-Tool {
    param([Parameter(Mandatory)] [string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required Android tool was not found: $Path"
    }
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory)] [string[]]$Arguments,
        [switch]$AllowFailure
    )

    $output = & $adb @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if (-not $AllowFailure -and $exitCode -ne 0) {
        throw "ADB failed with exit code ${exitCode}: $($output -join [Environment]::NewLine)"
    }
    return [pscustomobject]@{
        exitCode = $exitCode
        output = @($output)
    }
}

function Get-AccelerationStatus {
    $output = & $emulatorCheck accel 2>&1
    return [pscustomobject]@{
        exitCode = $LASTEXITCODE
        output = (@($output) -join [Environment]::NewLine).Trim()
    }
}

function Get-HostStatus {
    $os = Get-CimInstance Win32_OperatingSystem
    $cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
    $acceleration = Get-AccelerationStatus
    return [pscustomobject]@{
        sdkRoot = $sdkRoot
        emulatorVersion = (& $emulator -version 2>&1 | Select-Object -First 1)
        avdName = $AvdName
        serial = $Serial
        firmwareVirtualizationEnabled = [bool]$cpu.VirtualizationFirmwareEnabled
        hypervisorPresent = [bool](Get-CimInstance Win32_ComputerSystem).HypervisorPresent
        freeMemoryGb = [math]::Round($os.FreePhysicalMemory / 1MB, 2)
        totalMemoryGb = [math]::Round($os.TotalVisibleMemorySize / 1MB, 2)
        accelerationExitCode = $acceleration.exitCode
        acceleration = $acceleration.output
    }
}

function Assert-Acceleration {
    $status = Get-HostStatus
    if ($status.accelerationExitCode -ne 0) {
        throw @"
Android Emulator hardware acceleration is unavailable.
Firmware virtualization enabled: $($status.firmwareVirtualizationEnabled)
Hypervisor present: $($status.hypervisorPresent)
Emulator check: $($status.acceleration)

Enable AMD SVM in firmware, enable Windows Hypervisor Platform from an elevated
Windows session, reboot, and confirm that emulator-check.exe accel exits 0.
The script intentionally refuses -accel off because it did not boot reliably on
this x86_64 Windows host and would consume scarce RAM without producing a device.
"@
    }
    if ($status.freeMemoryGb -lt 2.5) {
        throw "Only $($status.freeMemoryGb) GB of RAM is free. Close Android Studio or other heavyweight applications before starting the AVD."
    }
}

function Test-DeviceOnline {
    $result = Invoke-Adb -Arguments @('-s', $Serial, 'get-state') -AllowFailure
    return $result.exitCode -eq 0 -and ($result.output -join '').Trim() -eq 'device'
}

function Wait-DeviceReady {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-DeviceOnline) {
            $boot = Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'getprop', 'sys.boot_completed') -AllowFailure
            if ($boot.exitCode -eq 0 -and ($boot.output -join '').Trim() -eq '1') {
                $avdResult = Invoke-Adb -Arguments @('-s', $Serial, 'emu', 'avd', 'name') -AllowFailure
                $actualAvd = @($avdResult.output | Where-Object { $_ -and $_ -ne 'OK' })[0]
                if ($avdResult.exitCode -ne 0 -or $actualAvd -ne $AvdName) {
                    throw "Serial $Serial is not the intended AVD. Expected '$AvdName', found '$actualAvd'."
                }
                Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
                Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'wm', 'dismiss-keyguard') -AllowFailure | Out-Null
                Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'settings', 'put', 'global', 'window_animation_scale', '0') | Out-Null
                Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'settings', 'put', 'global', 'transition_animation_scale', '0') | Out-Null
                Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'settings', 'put', 'global', 'animator_duration_scale', '0') | Out-Null
                Write-Output "AVD '$AvdName' is ready as $Serial."
                return
            }
        }
        Start-Sleep -Seconds 3
    }
    throw "AVD '$AvdName' did not finish booting within $TimeoutSeconds seconds."
}

function Assert-DeviceOnline {
    if (-not (Test-DeviceOnline)) {
        throw "Android target $Serial is not online. Start it with this script's Start action."
    }
}

function Resolve-OutputPath {
    param(
        [Parameter(Mandatory)] [string]$DefaultName,
        [Parameter(Mandatory)] [string]$Extension
    )

    $path = if ([string]::IsNullOrWhiteSpace($OutputPath)) {
        $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        Join-Path $evidenceRoot "$DefaultName-$timestamp.$Extension"
    } elseif ([System.IO.Path]::IsPathRooted($OutputPath)) {
        $OutputPath
    } else {
        Join-Path $repoRoot $OutputPath
    }
    $parent = Split-Path -Parent $path
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    return $path
}

Assert-Tool $adb
Assert-Tool $emulator
Assert-Tool $emulatorCheck

switch ($Action) {
    'Preflight' {
        $status = Get-HostStatus
        $status | Format-List
        Assert-Acceleration
    }
    'Status' {
        Get-HostStatus | Format-List
        & $adb devices -l
    }
    'Start' {
        if (Test-DeviceOnline) {
            Write-Output "Android target $Serial is already online."
            Wait-DeviceReady
            break
        }
        Assert-Acceleration
        $avds = @(& $emulator -list-avds 2>&1)
        if ($AvdName -notin $avds) {
            throw "AVD '$AvdName' does not exist. Available AVDs: $($avds -join ', ')"
        }
        New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
        $stdoutPath = Join-Path $evidenceRoot 'emulator.stdout.log'
        $stderrPath = Join-Path $evidenceRoot 'emulator.stderr.log'
        $arguments = @(
            '-avd', $AvdName,
            '-no-audio',
            '-no-boot-anim',
            '-gpu', 'host',
            '-memory', '2560',
            '-cores', '2',
            '-camera-back', 'none',
            '-camera-front', 'none'
        )
        if (-not $Windowed) {
            $arguments += '-no-window'
        }
        if ($ColdBoot) {
            $arguments += '-no-snapshot-load'
        }
        if ($WipeData) {
            $arguments += '-wipe-data'
        }
        $startParameters = @{
            FilePath = $emulator
            ArgumentList = $arguments
            PassThru = $true
            RedirectStandardOutput = $stdoutPath
            RedirectStandardError = $stderrPath
        }
        if (-not $Windowed) {
            $startParameters.WindowStyle = 'Hidden'
        }
        $process = Start-Process @startParameters
        Write-Output "Started '$AvdName' as process $($process.Id)."
        Wait-DeviceReady
    }
    'Wait' {
        Wait-DeviceReady
    }
    'Stop' {
        if (Test-DeviceOnline) {
            Invoke-Adb -Arguments @('-s', $Serial, 'emu', 'kill') | Out-Null
            Write-Output "Stop requested for $Serial."
        } else {
            Write-Output "Android target $Serial is not online."
        }
    }
    'Install' {
        Assert-DeviceOnline
        $resolvedApk = if ([string]::IsNullOrWhiteSpace($ApkPath)) { $defaultApk } else { $ApkPath }
        if (-not [System.IO.Path]::IsPathRooted($resolvedApk)) {
            $resolvedApk = Join-Path $repoRoot $resolvedApk
        }
        if (-not (Test-Path -LiteralPath $resolvedApk -PathType Leaf)) {
            throw "APK was not found: $resolvedApk. Build it with gradlew.bat :app:assembleDevelopmentDebug."
        }
        $result = Invoke-Adb -Arguments @('-s', $Serial, 'install', '-r', '-t', $resolvedApk)
        $result.output
    }
    'Launch' {
        Assert-DeviceOnline
        Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'am', 'start', '-W', '-n', "$PackageName/com.gurbakir.mobile.MainActivity") | Select-Object -ExpandProperty output
    }
    'StopApp' {
        Assert-DeviceOnline
        Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'am', 'force-stop', $PackageName) | Out-Null
        Write-Output "Stopped $PackageName."
    }
    'RestartApp' {
        Assert-DeviceOnline
        Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'am', 'force-stop', $PackageName) | Out-Null
        Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'am', 'start', '-W', '-n', "$PackageName/com.gurbakir.mobile.MainActivity") | Select-Object -ExpandProperty output
    }
    'Screenshot' {
        Assert-DeviceOnline
        $path = Resolve-OutputPath -DefaultName 'screen' -Extension 'png'
        $remotePath = '/sdcard/codex-gurbakir-screen.png'
        Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'screencap', '-p', $remotePath) | Out-Null
        Invoke-Adb -Arguments @('-s', $Serial, 'pull', $remotePath, $path) | Out-Null
        Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'rm', $remotePath) -AllowFailure | Out-Null
        Write-Output $path
    }
    'Hierarchy' {
        Assert-DeviceOnline
        $path = Resolve-OutputPath -DefaultName 'hierarchy' -Extension 'xml'
        $remotePath = '/sdcard/codex-gurbakir-hierarchy.xml'
        Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'uiautomator', 'dump', $remotePath) | Out-Null
        Invoke-Adb -Arguments @('-s', $Serial, 'pull', $remotePath, $path) | Out-Null
        Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'rm', $remotePath) -AllowFailure | Out-Null
        Write-Output $path
    }
    'Tap' {
        Assert-DeviceOnline
        Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'input', 'tap', "$X", "$Y") | Out-Null
        Write-Output "Tapped $X,$Y."
    }
    'Swipe' {
        Assert-DeviceOnline
        Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'input', 'swipe', "$X", "$Y", "$EndX", "$EndY", "$DurationMs") | Out-Null
        Write-Output "Swiped from $X,$Y to $EndX,$EndY in $DurationMs ms."
    }
    'Text' {
        Assert-DeviceOnline
        if ([string]::IsNullOrWhiteSpace($TextValue)) {
            throw 'TextValue is required for the Text action.'
        }
        if ($TextValue -notmatch '^[\x20-\x7E]+$') {
            throw 'The direct ADB Text action supports printable ASCII only. Use the MCP Unicode text tool when needed.'
        }
        $encoded = $TextValue.Replace(' ', '%s')
        Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'input', 'text', $encoded) | Out-Null
        Write-Output 'Text entered.'
    }
    'Key' {
        Assert-DeviceOnline
        Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'input', 'keyevent', $KeyCode) | Out-Null
        Write-Output "Sent $KeyCode."
    }
    'DeepLink' {
        Assert-DeviceOnline
        if ([string]::IsNullOrWhiteSpace($Uri)) {
            throw 'Uri is required for the DeepLink action.'
        }
        Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', $Uri, $PackageName) | Select-Object -ExpandProperty output
    }
    'Logcat' {
        Assert-DeviceOnline
        $pidResult = Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'pidof', $PackageName) -AllowFailure
        $pid = ($pidResult.output -join '').Trim()
        if ([string]::IsNullOrWhiteSpace($pid)) {
            throw "$PackageName is not running."
        }
        & $adb -s $Serial logcat -d -v threadtime --pid $pid
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    'Crashes' {
        Assert-DeviceOnline
        & $adb -s $Serial logcat -b crash -d -v threadtime
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
}

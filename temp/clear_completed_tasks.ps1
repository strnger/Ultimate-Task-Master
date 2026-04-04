# clear_completed_tasks.ps1
# Clears locally completed task data from RuneLite ConfigManager.
# Only removes completedTaskNames and pendingCompletions keys.
# Does NOT touch plan items, hidden tasks, beta unlock, or any other settings.
#
# Usage: Right-click -> Run with PowerShell
#   or: powershell -ExecutionPolicy Bypass -File clear_completed_tasks.ps1

$profileDir = "$env:USERPROFILE\.runelite\profiles2"
$targetKeys = @(
    'ultimate-task-master.completedTaskNames',
    'ultimate-task-master.pendingCompletions',
    'ultimate-task-master.completion-records'
)

Write-Host ""
Write-Host "=== UTM Clear Completed Tasks ===" -ForegroundColor Cyan
Write-Host ""

# Find all profile files
$profiles = Get-ChildItem "$profileDir\*.properties" -ErrorAction SilentlyContinue

if (-not $profiles) {
    Write-Host "No RuneLite profiles found at $profileDir" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

$totalCleared = 0

foreach ($profile in $profiles) {
    $lines = Get-Content $profile.FullName
    $originalCount = $lines.Count
    $removedKeys = @()

    $filtered = $lines | Where-Object {
        $line = $_
        $shouldRemove = $false
        foreach ($key in $targetKeys) {
            if ($line.StartsWith($key)) {
                $shouldRemove = $true
                $removedKeys += $key
                break
            }
        }
        -not $shouldRemove
    }

    if ($removedKeys.Count -gt 0) {
        # Write back the filtered content
        $filtered | Set-Content $profile.FullName
        $totalCleared += $removedKeys.Count
        Write-Host "  $($profile.Name):" -ForegroundColor Yellow
        foreach ($key in $removedKeys) {
            Write-Host "    Removed: $key" -ForegroundColor Green
        }
    }
}

Write-Host ""
if ($totalCleared -gt 0) {
    Write-Host "Done! Cleared $totalCleared key(s) across $($profiles.Count) profile(s)." -ForegroundColor Green
    Write-Host "Restart RuneLite for changes to take effect." -ForegroundColor Cyan
} else {
    Write-Host "No completed task data found to clear." -ForegroundColor Yellow
}

Write-Host ""
Read-Host "Press Enter to exit"

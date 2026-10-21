$service = Get-Service ssh-agent -ErrorAction SilentlyContinue

if (-not $service) {
    Write-Error "OpenSSH Authentication Agent is not installed."
    exit 1
}

if ($service.StartType -ne 'Automatic') {
    Set-Service -Name ssh-agent -StartupType Automatic
}

if ($service.Status -ne 'Running') {
    Start-Service ssh-agent
}

Get-Service ssh-agent | Format-Table Status, StartType, Name

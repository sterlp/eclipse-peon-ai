<#
.SYNOPSIS
  Removes platform bundles left behind by pre-fix Peon AI update sites (issue #142).

.DESCRIPTION
  Peon AI update sites built before 2026-10 were assembled with includeAllDependencies=true
  and therefore shipped the whole Eclipse target platform. Installing such a build on an
  older Eclipse left orphan bundles in the installation even after Peon AI was uninstalled:

    * org.objectweb.asm* 9.10.1    -> Eclipse fails to start (OSGi uses-constraint violation)
    * jakarta.annotation-api 3.0.0 -> Eclipse starts, but the "AI Peon" view stays blank
                                      (e4 matches @PostConstruct by annotation Class identity,
                                       so the plugin and e4 end up using different classes)

  This script deletes those stray JARs and removes their entries from
  configuration/org.eclipse.equinox.simpleconfigurator/bundles.info and from artifacts.xml.
  Both files are backed up (suffix .bak-<timestamp>) before they are changed.

.PARAMETER EclipseHome
  The Eclipse installation folder (the one containing eclipse.exe). Default: E:\eclipse

.PARAMETER DryRun
  Only report what would be removed; change nothing.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\clear-stale-platform-bundles.ps1 -EclipseHome "C:\eclipse"

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\clear-stale-platform-bundles.ps1 -DryRun
#>
param(
    [string]$EclipseHome = "E:\eclipse",
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

# Bundle ids/versions that pre-fix Peon AI update sites must never have shipped.
$stray = @(
    @{ id = 'org.objectweb.asm';                      ver = '9.10.1' },
    @{ id = 'org.objectweb.asm.tree';                 ver = '9.10.1' },
    @{ id = 'org.objectweb.asm.tree.analysis';        ver = '9.10.1' },
    @{ id = 'org.objectweb.asm.util';                 ver = '9.10.1' },
    @{ id = 'org.objectweb.asm.commons';              ver = '9.10.1' },
    @{ id = 'org.objectweb.asm.source';               ver = '9.10.1' },
    @{ id = 'org.objectweb.asm.tree.source';          ver = '9.10.1' },
    @{ id = 'org.objectweb.asm.tree.analysis.source'; ver = '9.10.1' },
    @{ id = 'org.objectweb.asm.util.source';          ver = '9.10.1' },
    @{ id = 'org.objectweb.asm.commons.source';       ver = '9.10.1' },
    @{ id = 'jakarta.annotation-api';                 ver = '3.0.0'  },
    @{ id = 'jakarta.annotation-api.source';          ver = '3.0.0'  }
)

if (-not (Test-Path -LiteralPath (Join-Path $EclipseHome 'eclipse.exe'))) {
    throw "'$EclipseHome' does not look like an Eclipse installation (no eclipse.exe). Pass -EclipseHome <path>."
}

$plugins   = Join-Path $EclipseHome 'plugins'
$bundles   = Join-Path $EclipseHome 'configuration\org.eclipse.equinox.simpleconfigurator\bundles.info'
$artifacts = Join-Path $EclipseHome 'artifacts.xml'

if (-not $DryRun) {
    $running = Get-Process eclipse, javaw -ErrorAction SilentlyContinue
    if ($running) {
        throw "Eclipse appears to be running (pid $($running.Id -join ',')). Close it completely and re-run."
    }
}

$stamp            = Get-Date -Format 'yyyyMMdd-HHmmss'
$removedJars      = 0
$removedLines     = 0
$removedArtifacts = 0

# 1) stray JARs -----------------------------------------------------------------
foreach ($s in $stray) {
    $file = Join-Path $plugins ("{0}_{1}.jar" -f $s.id, $s.ver)
    if (Test-Path -LiteralPath $file) {
        Write-Host "JAR     $($s.id) $($s.ver)"
        if (-not $DryRun) {
            Copy-Item -LiteralPath $file -Destination "$file.bak-$stamp"
            Remove-Item -LiteralPath $file
        }
        $removedJars++
    }
}

# 2) bundles.info ---------------------------------------------------------------
if (Test-Path -LiteralPath $bundles) {
    $text = [System.IO.File]::ReadAllText($bundles)
    $new  = $text
    foreach ($s in $stray) {
        $pat = '(?m)^' + [regex]::Escape($s.id) + ',' + [regex]::Escape($s.ver) + ',.*\r?\n?'
        if ([regex]::IsMatch($new, $pat)) {
            Write-Host "INFO    $($s.id) $($s.ver)"
            $new = [regex]::Replace($new, $pat, '')
            $removedLines++
        }
    }
    if (-not $DryRun -and $new -ne $text) {
        Copy-Item -LiteralPath $bundles -Destination "$bundles.bak-$stamp"
        [System.IO.File]::WriteAllText($bundles, $new, (New-Object System.Text.UTF8Encoding($false)))
    }
}

# 3) artifacts.xml (bundle-pool index) ------------------------------------------
if (Test-Path -LiteralPath $artifacts) {
    $xml = [System.IO.File]::ReadAllText($artifacts)
    $new = $xml
    foreach ($s in $stray) {
        $pat = "(?s)\s*<artifact [^>]*id='" + [regex]::Escape($s.id) +
               "' version='" + [regex]::Escape($s.ver) + "'>.*?</artifact>"
        $c = ([regex]::Matches($new, $pat)).Count
        if ($c -gt 0) {
            Write-Host "POOL    $($s.id) $($s.ver)"
            $new = [regex]::Replace($new, $pat, '')
            $removedArtifacts += $c
        }
    }
    if (-not $DryRun -and $new -ne $xml) {
        $after = ([regex]::Matches($new, '<artifact ')).Count
        $new = [regex]::Replace($new, "<artifacts size='\d+'", "<artifacts size='$after'", 1)
        [xml]$null = $new   # validate before writing
        Copy-Item -LiteralPath $artifacts -Destination "$artifacts.bak-$stamp"
        [System.IO.File]::WriteAllText($artifacts, $new, (New-Object System.Text.UTF8Encoding($false)))
    }
}

Write-Host ''
if ($DryRun) {
    Write-Host ("DRY RUN - nothing changed. Would remove {0} JAR(s), {1} bundles.info line(s), {2} pool entr(y/ies)." -f $removedJars, $removedLines, $removedArtifacts)
} else {
    Write-Host ("Removed {0} JAR(s), {1} bundles.info line(s), {2} pool entr(y/ies)." -f $removedJars, $removedLines, $removedArtifacts)
    Write-Host "Backups were written with suffix .bak-$stamp"
    Write-Host ''
    Write-Host 'Next: start Eclipse once with  eclipse.exe -clean -clearPersistedState'
}

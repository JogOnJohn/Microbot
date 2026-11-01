param(
    [Parameter(Mandatory=$true)][string]$Results,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [Parameter(Mandatory=$true)][string]$Label
)
$ErrorActionPreference = 'Stop'
$rows = @(Get-ChildItem -LiteralPath $Results -Filter 'TEST-*.xml' | ForEach-Object {
    [xml]$report = Get-Content -LiteralPath $_.FullName -Raw
    $suite = $report.testsuite
    [pscustomobject]@{
        suite = [string]$suite.name
        tests = [int]$suite.tests
        failures = [int]$suite.failures
        errors = [int]$suite.errors
        skipped = [int]$suite.skipped
        seconds = [double]::Parse($suite.time, [Globalization.CultureInfo]::InvariantCulture)
    }
})
if (!$rows.Count) { throw 'No JUnit XML results found' }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$summary = [ordered]@{
    suites = $rows.Count
    tests = ($rows | Measure-Object tests -Sum).Sum
    failures = ($rows | Measure-Object failures -Sum).Sum
    errors = ($rows | Measure-Object errors -Sum).Sum
    skipped = ($rows | Measure-Object skipped -Sum).Sum
    results = $rows
}
$summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutputDirectory ($Label + '-summary.json')) -Encoding UTF8
$xmlDirectory = Join-Path $OutputDirectory ($Label + '-xml')
New-Item -ItemType Directory -Force -Path $xmlDirectory | Out-Null
Get-ChildItem -LiteralPath $Results -Filter 'TEST-*.xml' | Copy-Item -Destination $xmlDirectory
[pscustomobject]$summary | Select-Object suites, tests, failures, errors, skipped | ConvertTo-Json
if ($summary.failures -or $summary.errors) { exit 1 }

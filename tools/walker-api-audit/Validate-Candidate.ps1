[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{40}$')][string]$CandidateCommit,
    [Parameter(Mandatory=$true)][string]$GeneratedDirectory,
    [Parameter(Mandatory=$true)][string]$EvidenceDirectory,
    [Parameter(Mandatory=$true)][string]$ArtifactDirectory,
    [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot',
    [string]$Gradle = 'C:\Users\bizz4\.gradle\wrapper\dists\gradle-8.8-all\6gdy1pgp427xkqcjbxw3ylt6h\gradle-8.8\bin\gradle.bat'
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
function Git-Read([string[]]$Arguments) {
    $result = & git -c "safe.directory=$($repo.Replace('\','/'))" -C $repo @Arguments
    if ($LASTEXITCODE) { throw "git failed: $Arguments" }
    return $result
}
function Assert-Source {
    if ((Git-Read @('rev-parse','HEAD')) -ne $CandidateCommit) { throw 'Candidate HEAD changed' }
    if (Git-Read @('status','--porcelain=v1','--untracked-files=all')) { throw 'Commit/review the candidate tree before validation' }
}
Assert-Source
$generated = (Resolve-Path -LiteralPath $GeneratedDirectory).Path
if (!(Test-Path -LiteralPath (Join-Path $generated 'sync-provenance.properties'))) { throw 'Missing generated provenance' }
if (Test-Path -LiteralPath $EvidenceDirectory) { throw 'Use a fresh evidence directory' }
if (!(Test-Path -LiteralPath $Gradle) -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'Missing cached build runtime' }
New-Item -ItemType Directory -Path $EvidenceDirectory,$ArtifactDirectory -Force | Out-Null
$oldJavaHome = $env:JAVA_HOME
Push-Location $repo
try {
    $env:JAVA_HOME = $JavaHome
    $common = @('--offline','--no-daemon','--max-workers=1','--build-cache','--console=plain',
        '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8','-Dorg.gradle.java.installations.auto-download=false',
        '-I',(Join-Path $PSScriptRoot 'candidate-tests.init.gradle'),"-PwalkerGeneratedDir=$generated")
    function Run-Gradle([string]$Label, [string[]]$Tasks) {
        Assert-Source
        & $Gradle @common @Tasks *> (Join-Path $EvidenceDirectory ($Label + '.log'))
        if ($LASTEXITCODE) { throw "Gradle failed: $Label; inspect its evidence log" }
        Assert-Source
    }
    Run-Gradle 'full-client-tests' @(':client:runUnitTests')
    & (Join-Path $PSScriptRoot 'Summarize-Tests.ps1') -Results 'runelite-client/build/test-results/runUnitTests' -OutputDirectory $EvidenceDirectory -Label full-client
    if ($LASTEXITCODE) { throw 'Client test summary contains failures' }
    $summary = Get-Content -LiteralPath (Join-Path $EvidenceDirectory 'full-client-summary.json') -Raw | ConvertFrom-Json
    foreach ($required in @('ShortestPathGoldenRouteBaselineTest','WalkerRouteCorpusTest',
        'PersonalPohSelectionTest','World330HostedHouseAccessTest','World330HostedHouseTransportTest',
        'BankedTransportItemPlanningTest','InteractionReconciliationTest','TypedRoutePlanningTest',
        'TypedBankRouteProbeTest','TypedPlanningActiveRouteIsolationTest','TransportEdgeBankingTest','TransportEdgeAdapterResourceTest',
        'TransportEdgeBankingCaptureTest','TransportEdgeAdapterTest','Rs2TransportValuesTest',
        'LegacyRouteStepsTest','OwnedPlanningRefreshTest','LeaguesPlanningSnapshotTest',
        'PlanningConfigCaptureTest','PlanningHouseSnapshotTest','PlanningPohReadOnlyTest',
        'Rs2NpcModelActionSlotTest','Rs2TileObjectModelActionSlotTest',
        'ClientThreadGuardrailTest','QueryableTerminalGuardrailTest')) {
        $suite = @($summary.results | Where-Object { $_.suite.EndsWith('.' + $required) })
        if ($suite.Count -ne 1 -or $suite[0].tests -le 0 -or $suite[0].skipped -ne 0) {
            throw "Required suite missing, empty or skipped: $required"
        }
    }
    Run-Gradle 'generated-validator' @(':client:validateTransportSync',"-PtransportSyncGeneratedDir=$generated")
    & (Join-Path $PSScriptRoot 'Summarize-Tests.ps1') -Results 'runelite-client/build/test-results/validateTransportSync' -OutputDirectory $EvidenceDirectory -Label generated-validator
    if ($LASTEXITCODE) { throw 'Generated validator contains failures' }
    $validator = Get-Content -LiteralPath (Join-Path $EvidenceDirectory 'generated-validator-summary.json') -Raw | ConvertFrom-Json
    if ($validator.tests -le 0 -or $validator.skipped -ne 0) { throw 'Generated validator did not execute fully' }
    Run-Gradle 'offline-benchmark' @(':client:walkerApiBenchmark')
    $rows = @(Get-Content -LiteralPath (Join-Path $EvidenceDirectory 'offline-benchmark.log') | Where-Object { $_ -match '^(LEGACY|EXACT),' })
    $measurements = @($rows | ConvertFrom-Csv -Header backend,fixture,bankSnapshot,variant,samples,p50Nanos,p95Nanos,maxNanos,elapsedNanos,heapDeltaBytes,gcCountDelta)
    if ($measurements.Count -ne 20) { throw 'Incomplete benchmark matrix' }
    foreach ($pair in ($measurements | Group-Object backend,fixture,bankSnapshot)) {
        $direct = @($pair.Group | Where-Object variant -eq 'direct')
        $typed = @($pair.Group | Where-Object variant -eq 'typed')
        if ($direct.Count -ne 1 -or $typed.Count -ne 1) { throw 'Incomplete benchmark pair' }
        if ([double]$typed[0].p95Nanos -gt 3 * [double]$direct[0].p95Nanos + 25000000) { throw "Benchmark needs investigation: $($pair.Name)" }
    }
    $measurements | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'offline-benchmark.json') -Encoding UTF8
    Run-Gradle 'test-jar-build' @(':client:microbotReleaseJar')
    $versionLine = @(Get-Content -LiteralPath 'gradle.properties' | Where-Object { $_ -match '^microbot\.version\s*=' })
    if ($versionLine.Count -ne 1) { throw 'Ambiguous Microbot version' }
    $version = ($versionLine[0] -split '=',2)[1].Trim()
    if ($version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') { throw 'Unexpected release version' }
    $source = Join-Path $repo "runelite-client/build/libs/microbot-$version.jar"
    $artifact = Join-Path $ArtifactDirectory "microbot-$version-walker-api-$($CandidateCommit.Substring(0,10)).jar"
    if (Test-Path -LiteralPath $artifact) { throw 'Refusing to overwrite a versioned test artifact' }
    Assert-Source
    Copy-Item -LiteralPath $source -Destination $artifact
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($artifact)
    try {
        foreach ($required in @('META-INF/MANIFEST.MF','net/runelite/client/RuneLite.class',
            'net/runelite/client/plugins/microbot/util/walker/TypedRoutePlanning.class',
            'net/runelite/client/plugins/microbot/util/walker/interaction/InteractionRequest.class',
            'net/runelite/client/plugins/microbot/shortestpath/pathfinder/LegacyRouteSteps.class')) {
            if (!$zip.GetEntry($required)) { throw "Incomplete test client JAR: $required" }
        }
    } finally { $zip.Dispose() }
    [ordered]@{
        kind='isolated-walker-api-test-client';sourceCommit=$CandidateCommit;sourceClean=$true
        artifact=$artifact;sha256=(Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
        generatedDirectory=$generated;evidence=$EvidenceDirectory;javaHome=$JavaHome
        clientOfflineValidation=$true;hubValidation='separate exact-artifact matrix required'
        installed=$false;launched=$false;promoted=$false;pushed=$false
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'test-client-manifest.json') -Encoding UTF8
    Write-Output $artifact
} finally {
    Pop-Location
    $env:JAVA_HOME = $oldJavaHome
}

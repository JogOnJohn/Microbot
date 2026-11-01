param(
    [Parameter(Mandatory=$true)][string]$CandidateXml,
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{40}$')][string]$CandidateCommit,
    [string]$InventoryDirectory = (Join-Path $PSScriptRoot '../../docs/walker-api-synthesis/inventory')
)
$ErrorActionPreference = 'Stop'
function Key($row) { "$($row.kind):$($row.owner):$($row.name)($($row.params))" }
[xml]$candidate = Get-Content -LiteralPath $CandidateXml -Raw
$current = @{}
foreach ($row in @($candidate.inventory.row)) {
    if ($row.external -eq 'true') { $current[(Key $row)] = $row }
}
if (!$current.Count) { throw 'Candidate inventory contains no external declarations' }
$sources = @{}
foreach ($variant in @('playable','runtime','data-sync','upstream-development','upstream-main')) {
    foreach ($row in (Import-Csv -LiteralPath (Join-Path $InventoryDirectory ($variant + '-api.csv')))) {
        if ($row.external -ne 'true') { continue }
        $key = Key $row
        if (!$sources.ContainsKey($key)) { $sources[$key] = [ordered]@{ row = $row; variants = @() } }
        $sources[$key].variants += $variant
    }
}
foreach ($key in $current.Keys) {
    if (!$sources.ContainsKey($key)) {
        $sources[$key] = [ordered]@{ row = $current[$key]; variants = @('candidate') }
    }
}
$out = @(foreach ($key in ($sources.Keys | Sort-Object)) {
    $entry = $sources[$key]
    $row = $entry.row
    $present = $current.ContainsKey($key)
    $legacy = $entry.variants -contains 'playable'
    if ($present) {
        $disposition = if ($legacy) { 'preserved-declaration' }
            elseif ($entry.variants -contains 'candidate') { 'added-declaration' }
            else { 'adapted-declaration' }
        $reason = 'Source declaration present; semantic and binary evidence is recorded separately.'
    } else {
        $disposition = 'intentionally-unsupported'
        $reason = switch -Regex ($row.owner) {
            'Shadow|Canary|Comparison|UpstreamRoutePlanner|PlannerSelection|Rs2ActiveRoute' {
                'Optional alternate-engine rollout and background diagnostics are not enabled by API synthesis.'; break
            }
            'Movement|Doors|Transports|TransportResolver|RouteCamera|SceneObject|Recovery' {
                'Upstream split-executor implementation is not imported; retain the tested local executor and camera policy.'; break
            }
            'Rs2RoutePlanner|Rs2PlanningSnapshot' {
                'No alternate engine contract is published; isolated local planning uses a search-owned config behind the facade.'; break
            }
            'InteractionApproach' {
                'Only fresh active-top-level GameObject and WallObject readiness is supported; unsupported coordinate/model variants fail closed.'; break
            }
            default {
                'Outside the selected compatibility surface; retain existing local entry points rather than expose unvalidated upstream internals.'
            }
        }
        if ($legacy) { throw "Existing local declaration disappeared: $key" }
    }
    [pscustomobject]@{
        owner = $row.owner; kind = $row.kind; name = $row.name; params = $row.params
        sources = ($entry.variants -join ';'); disposition = $disposition; reason = $reason
        candidateCommit = $CandidateCommit; syntaxOnly = $true
    }
})
$path = Join-Path $InventoryDirectory 'api-dispositions.csv'
$out | Export-Csv -LiteralPath $path -NoTypeInformation -Encoding UTF8
$out | Group-Object disposition | Select-Object Name, Count | ConvertTo-Json

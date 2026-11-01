[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Artifact,
    [Parameter(Mandatory=$true)][string]$OutputFile
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Artifact).Path)
$sha = [Security.Cryptography.SHA256]::Create()
$counts = [ordered]@{}
try {
    foreach ($module in @('runelite-api','runelite-client')) {
        $root = (Resolve-Path -LiteralPath (Join-Path $repo "$module/build/classes/java/main")).Path
        $files = @(Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.class')
        if (!$files.Count) { throw "No compiled production classes: $module" }
        foreach ($file in $files) {
            $name = $file.FullName.Substring($root.Length + 1).Replace('\','/')
            $entry = $zip.GetEntry($name)
            if (!$entry) { throw "Missing artifact class: $name" }
            $stream = $entry.Open()
            try { $packed = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','') }
            finally { $stream.Dispose() }
            if ($packed -ne (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash) {
                throw "Production bytecode differs: $name"
            }
        }
        $counts[$module] = $files.Count
    }
} finally { $sha.Dispose(); $zip.Dispose() }
[ordered]@{
    artifact=(Resolve-Path -LiteralPath $Artifact).Path
    sha256=(Get-FileHash -LiteralPath $Artifact -Algorithm SHA256).Hash.ToLowerInvariant()
    compiledClassCounts=$counts
    allCompiledProductionClassesIdentical=$true
    scope='Compiled API/client Java main classes; not resources or third-party dependencies'
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $OutputFile -Encoding UTF8
